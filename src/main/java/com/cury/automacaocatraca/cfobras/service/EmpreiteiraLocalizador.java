package com.cury.automacaocatraca.cfobras.service;

import com.cury.automacaocatraca.domain.entity.EmpreiteiraCache;
import com.cury.automacaocatraca.domain.entity.EmpreiteiraObraVinculo;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import com.cury.automacaocatraca.repository.EmpreiteiraCacheRepository;
import com.cury.automacaocatraca.repository.EmpreiteiraObraVinculoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class EmpreiteiraLocalizador {

    private static final Logger log = LoggerFactory.getLogger(EmpreiteiraLocalizador.class);

    private static final int TAMANHO_MINIMO_PARCIAL = 6;

    private final EmpreiteiraCacheRepository empreiteiraRepository;
    private final EmpreiteiraObraVinculoRepository vinculoRepository;

    public EmpreiteiraLocalizador(EmpreiteiraCacheRepository empreiteiraRepository,
                                  EmpreiteiraObraVinculoRepository vinculoRepository) {
        this.empreiteiraRepository = empreiteiraRepository;
        this.vinculoRepository = vinculoRepository;
    }

    @Transactional
    public Optional<EmpreiteiraCache> localizar(String idCfObras, String cnpj,
                                                List<String> nomes, List<String> avisos) {
        Optional<EmpreiteiraCache> peloId = porIdCfObras(idCfObras, avisos);

        if (peloId.isPresent()) {
            return peloId;
        }

        Optional<EmpreiteiraCache> peloCnpj = porCnpj(cnpj);

        if (peloCnpj.isPresent()) {
            return peloCnpj;
        }

        if (nomes != null) {
            for (String nome : nomes) {
                Optional<EmpreiteiraCache> peloNome = porNome(nome);

                if (peloNome.isPresent()) {
                    return peloNome;
                }
            }
        }

        return Optional.empty();
    }

    @Transactional
    public Optional<EmpreiteiraCache> porIdCfObras(String idCfObras, List<String> avisos) {
        if (vazio(idCfObras)) {
            return Optional.empty();
        }

        List<EmpreiteiraCache> achadas = empreiteiraRepository.findAllByIdCfObras(idCfObras);

        if (achadas.isEmpty()) {
            return Optional.empty();
        }

        if (achadas.size() == 1) {
            return Optional.of(achadas.get(0));
        }

        return Optional.of(consolidar(achadas, avisos));
    }

    public Optional<EmpreiteiraCache> porCnpj(String cnpj) {
        String digitos = somenteDigitos(cnpj);

        if (vazio(digitos)) {
            return Optional.empty();
        }

        return empreiteiraRepository.findByCnpj(digitos);
    }

    public Optional<EmpreiteiraCache> porNome(String nome) {
        if (vazio(nome)) {
            return Optional.empty();
        }

        String alvo = compactar(NormalizadorNome.normalizar(nome));

        if (alvo.isBlank()) {
            return Optional.empty();
        }

        List<EmpreiteiraCache> exatas = new ArrayList<>();
        List<EmpreiteiraCache> parciais = new ArrayList<>();

        for (EmpreiteiraCache registro : empreiteiraRepository.findAll()) {
            boolean exata = false;
            boolean parcial = false;

            for (String candidato : nomesDe(registro)) {
                String comparado = compactar(NormalizadorNome.normalizar(candidato));

                if (comparado.isBlank()) {
                    continue;
                }

                if (comparado.equals(alvo)) {
                    exata = true;
                    break;
                }

                if (comparado.length() > TAMANHO_MINIMO_PARCIAL
                        && alvo.length() > TAMANHO_MINIMO_PARCIAL
                        && (comparado.contains(alvo) || alvo.contains(comparado))) {
                    parcial = true;
                }
            }

            if (exata) {
                exatas.add(registro);
            } else if (parcial) {
                parciais.add(registro);
            }
        }

        return melhor(exatas).or(() -> melhor(parciais));
    }

    private EmpreiteiraCache consolidar(List<EmpreiteiraCache> repetidas, List<String> avisos) {
        List<EmpreiteiraCache> ordenadas = new ArrayList<>(repetidas);
        ordenadas.sort(ordemDePreferencia());

        EmpreiteiraCache vencedora = ordenadas.get(0);

        for (int i = 1; i < ordenadas.size(); i++) {
            EmpreiteiraCache perdedora = ordenadas.get(i);

            String cnpj = perdedora.getCnpj();
            String razao = perdedora.getRazaoSocial();
            String fantasia = perdedora.getNomeFantasia();
            String cor = perdedora.getCorIdentificacao();
            boolean cadastrada = perdedora.isCadastradaNoCfObras();
            String nomePerdedora = perdedora.getNomeTrc();
            Long idPerdedora = perdedora.getId();

            transferirVinculos(vencedora, perdedora);

            empreiteiraRepository.delete(perdedora);
            empreiteiraRepository.flush();

            if (vazio(vencedora.getCnpj()) && !vazio(cnpj)) {
                vencedora.setCnpj(cnpj);
            }
            if (vazio(vencedora.getRazaoSocial()) && !vazio(razao)) {
                vencedora.setRazaoSocial(razao);
            }
            if (vazio(vencedora.getNomeFantasia()) && !vazio(fantasia)) {
                vencedora.setNomeFantasia(fantasia);
            }
            if (vazio(vencedora.getCorIdentificacao()) && !vazio(cor)) {
                vencedora.setCorIdentificacao(cor);
            }
            if (cadastrada) {
                vencedora.setCadastradaNoCfObras(true);
            }

            log.warn("Duplicata no banco: '{}' (id {}) tinha o mesmo id do CF Obras de '{}' (id {}) — consolidada e apagada",
                    nomePerdedora, idPerdedora, vencedora.getNomeTrc(), vencedora.getId());

            if (avisos != null) {
                avisos.add("duplicata consolidada automaticamente: '" + nomePerdedora
                        + "' (id " + idPerdedora + ") virou '" + vencedora.getNomeTrc()
                        + "' (id " + vencedora.getId() + ")");
            }
        }

        vencedora.setDataUltimaVerificacao(LocalDateTime.now());

        return empreiteiraRepository.save(vencedora);
    }

    private void transferirVinculos(EmpreiteiraCache vencedora, EmpreiteiraCache perdedora) {
        for (EmpreiteiraObraVinculo vinculo : vinculoRepository.findByEmpreiteira(perdedora)) {
            if (vinculoRepository.findByEmpreiteiraAndObra(vencedora, vinculo.getObra()).isEmpty()) {
                vinculo.setEmpreiteira(vencedora);
                vinculoRepository.save(vinculo);
            } else {
                vinculoRepository.delete(vinculo);
            }
        }

        vinculoRepository.flush();
    }

    private Optional<EmpreiteiraCache> melhor(List<EmpreiteiraCache> candidatas) {
        return candidatas.stream().min(ordemDePreferencia());
    }

    private Comparator<EmpreiteiraCache> ordemDePreferencia() {
        return Comparator
                .comparing((EmpreiteiraCache e) -> vazio(e.getIdCfObras()))
                .thenComparing(e -> vazio(e.getCnpj()))
                .thenComparing(e -> vazio(e.getCorIdentificacao()))
                .thenComparing(e -> e.getId() == null ? Long.MAX_VALUE : e.getId());
    }

    private List<String> nomesDe(EmpreiteiraCache registro) {
        return List.of(
                registro.getNomeTrc() == null ? "" : registro.getNomeTrc(),
                registro.getNomeNormalizado() == null ? "" : registro.getNomeNormalizado(),
                registro.getRazaoSocial() == null ? "" : registro.getRazaoSocial(),
                registro.getNomeFantasia() == null ? "" : registro.getNomeFantasia());
    }

    private String compactar(String texto) {
        return texto == null ? "" : texto.replaceAll("[^A-Z0-9]", "");
    }

    private String somenteDigitos(String texto) {
        return texto == null ? null : texto.replaceAll("\\D", "");
    }

    private boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}