package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.cfobras.dto.PendenciaFuncionario;
import com.cury.automacaocatraca.cfobras.dto.PlanoConciliacao;
import com.cury.automacaocatraca.cfobras.page.CadastroFuncionarioPage;
import com.cury.automacaocatraca.domain.dto.DadosCadastroFuncionario;
import com.cury.automacaocatraca.domain.entity.FuncionarioCache;
import com.cury.automacaocatraca.domain.enums.NivelAcesso;
import com.cury.automacaocatraca.domain.mapper.CadastroMapper;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import com.cury.automacaocatraca.repository.FuncionarioCacheRepository;
import com.cury.automacaocatraca.trc.TrcFuncionarioExtractor;
import com.cury.automacaocatraca.trc.dto.FuncionarioTrc;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class ConciliacaoFuncionariosStep {

    private static final Logger log = LoggerFactory.getLogger(ConciliacaoFuncionariosStep.class);

    private final TrcFuncionarioExtractor trcExtractor;
    private final CadastroFuncionarioPage cadastroPage;
    private final CadastroMapper cadastroMapper;
    private final FuncionarioCacheRepository funcionarioRepository;

    public ConciliacaoFuncionariosStep(TrcFuncionarioExtractor trcExtractor,
                                       CadastroFuncionarioPage cadastroPage,
                                       CadastroMapper cadastroMapper,
                                       FuncionarioCacheRepository funcionarioRepository) {
        this.trcExtractor = trcExtractor;
        this.cadastroPage = cadastroPage;
        this.cadastroMapper = cadastroMapper;
        this.funcionarioRepository = funcionarioRepository;
    }

    public Resumo executar(WebDriver trc, WebDriver cf, PlanoConciliacao plano) {
        return executar(trc, cf, plano, null);
    }

    public Resumo executar(WebDriver trc, WebDriver cf, PlanoConciliacao plano, Cronometro cronometro) {
        Resumo resumo = new Resumo();

        Map<String, Set<String>> porEmpreiteira = agrupar(plano.funcionariosParaCadastrar());

        if (porEmpreiteira.isEmpty()) {
            log.info("Nenhum funcionario pendente");
            return resumo;
        }

        cadastroPage.abrir(cf);

        for (Map.Entry<String, Set<String>> grupo : porEmpreiteira.entrySet()) {
            String empreiteira = grupo.getKey();
            Set<String> funcionarios = grupo.getValue();

            Optional<String> idCard = cadastroPage.abrirFuncionariosDe(cf, empreiteira);

            if (idCard.isEmpty()) {
                log.warn("[SEM CARD] {} — {} funcionarios ficam de fora",
                        empreiteira, funcionarios.size());
                resumo.semCard.add(empreiteira);
                resumo.naoCadastrados.addAll(funcionarios);
                continue;
            }

            log.info("--- {} ({} funcionarios pendentes) ---", empreiteira, funcionarios.size());

            for (String nome : funcionarios) {
                Cronometro.Marcacao medicao = abrir(cronometro, nome + " (" + empreiteira + ")");

                try {
                    processar(trc, cf, empreiteira, nome, resumo);
                } catch (Exception e) {
                    log.error("[ERRO] {} ({}) -> {}", nome, empreiteira, e.getMessage());
                    resumo.erros.add(nome + ": " + e.getMessage());
                    resumo.naoCadastrados.add(nome);
                } finally {
                    fechar(medicao);
                }
            }

            cadastroPage.fechar(cf);
        }

        log.info(resumo.resumoTexto());

        return resumo;
    }

    private Cronometro.Marcacao abrir(Cronometro cronometro, String detalhe) {
        return cronometro == null ? null : cronometro.iniciar(Cronometro.ETAPA_FUNCIONARIOS, detalhe);
    }

    private void fechar(Cronometro.Marcacao medicao) {
        if (medicao != null) {
            medicao.fechar();
        }
    }

    private void processar(WebDriver trc, WebDriver cf, String empreiteira,
                           String nome, Resumo resumo) {

        if (cadastroPage.jaCadastrado(cf, nome, "")) {
            log.info("[JA EXISTE] {}", nome);
            resumo.jaExistiam.add(nome);
            completarPeloTrc(trc, nome, empreiteira);
            return;
        }

        Optional<FuncionarioTrc> origemOpt = trcExtractor.extrairFuncionario(trc, nome, empreiteira);

        if (origemOpt.isEmpty()) {
            log.warn("[PULANDO] {} — nao encontrado no TRC (nada gravado no cache)", nome);
            resumo.semCadastroNoTrc.add(nome + " (" + empreiteira + ")");
            resumo.naoCadastrados.add(nome);
            return;
        }

        FuncionarioTrc origem = origemOpt.get();
        DadosCadastroFuncionario destino = cadastroMapper.mapear(origem);

        if (destino.cpf() == null || destino.cpf().isBlank()) {
            log.warn("[PULANDO] {} — sem CPF no TRC (nada gravado no cache)", nome);
            resumo.semCpf.add(nome + " (" + empreiteira + ")");
            resumo.naoCadastrados.add(nome);
            return;
        }

        if (cadastroPage.jaCadastrado(cf, destino.nomeCompleto(), destino.cpf())) {
            log.info("[JA EXISTE] {} (achado pelo CPF)", nome);
            resumo.jaExistiam.add(nome);
            salvarNoBanco(nome, empreiteira, destino.cpf(), origem.funcao(), true);
            return;
        }

        log.info("[CADASTRANDO] {} | CPF {} | funcao '{}' | nivel {}",
                nome, destino.cpf(), destino.funcao(), destino.nivelAcesso());

        CadastroFuncionarioPage.ResultadoCadastro resultado = cadastroPage.cadastrar(cf, destino);

        if (!resultado.sucesso()) {
            log.warn("[FALHOU] {} -> {}", nome, resultado.mensagem());
            resumo.naoCadastrados.add(nome);
            resumo.erros.add(nome + ": " + resultado.mensagem());
            return;
        }

        salvarNoBanco(nome, empreiteira, destino.cpf(), origem.funcao(), true);

        resumo.cadastrados.add(nome);

        if (cadastroPage.caiuNoFallback(resultado.funcaoSelecionada())) {
            resumo.funcoesDesconhecidas.add(origem.funcao() + " (" + nome + ")");
        }

        if (destino.nivelAcesso() == NivelAcesso.GESTOR) {
            resumo.atribuicoesGestor.add(nome + " — funcao '" + origem.funcao() + "'");
        }
    }

    private void completarPeloTrc(WebDriver trc, String nome, String empreiteira) {
        Optional<TrcFuncionarioExtractor.LinhaTrc> linha =
                trcExtractor.localizarNaListagem(trc, nome);

        if (linha.isEmpty()) {
            log.warn("'{}' ja existe no CF Obras mas nao foi achado na listagem do TRC "
                    + "— cache fica sem CPF e funcao", nome);
            salvarNoBanco(nome, empreiteira, null, null, true);
            return;
        }

        salvarNoBanco(nome, empreiteira, linha.get().cpf(), linha.get().funcao(), true);
    }

    private void salvarNoBanco(String nomeTrc, String empreiteiraTrc, String cpf,
                               String funcao, boolean cadastrado) {

        String nomeNormalizado = NormalizadorNome.normalizar(nomeTrc);
        String empreiteiraNormalizada = NormalizadorNome.normalizar(empreiteiraTrc);

        FuncionarioCache registro = funcionarioRepository
                .findByNomeNormalizadoAndEmpreiteiraNomeNormalizado(nomeNormalizado, empreiteiraNormalizada)
                .orElseGet(FuncionarioCache::new);

        registro.setNomeTrc(nomeTrc);
        registro.setNomeNormalizado(nomeNormalizado);
        registro.setEmpreiteiraNomeTrc(empreiteiraTrc);
        registro.setEmpreiteiraNomeNormalizado(empreiteiraNormalizada);
        registro.setCadastradoNoCfObras(cadastrado);
        registro.setDataUltimaVerificacao(LocalDateTime.now());

        if (cpf != null && !cpf.isBlank()) {
            registro.setCpf(cpf);
        }

        if (funcao != null && !funcao.isBlank()) {
            registro.setFuncao(funcao);
        }

        try {
            funcionarioRepository.save(registro);
        } catch (Exception e) {
            log.warn("Nao consegui gravar '{}' no cache: {}", nomeTrc, e.getMessage());
        }
    }

    private Map<String, Set<String>> agrupar(List<PendenciaFuncionario> pendencias) {
        Map<String, Set<String>> mapa = new LinkedHashMap<>();

        for (PendenciaFuncionario pendencia : pendencias) {
            mapa.computeIfAbsent(pendencia.nomeEmpreiteiraTrc(), k -> new LinkedHashSet<>())
                    .add(pendencia.nomeFuncionarioTrc());
        }

        return mapa;
    }

    public static final class Resumo {
        public final List<String> cadastrados = new ArrayList<>();
        public final List<String> jaExistiam = new ArrayList<>();
        public final List<String> naoCadastrados = new ArrayList<>();
        public final List<String> semCadastroNoTrc = new ArrayList<>();
        public final List<String> semCpf = new ArrayList<>();
        public final List<String> semCard = new ArrayList<>();
        public final List<String> funcoesDesconhecidas = new ArrayList<>();
        public final List<String> atribuicoesGestor = new ArrayList<>();
        public final List<String> erros = new ArrayList<>();

        public String resumoTexto() {
            return String.format(
                    "Funcionarios — cadastrados: %d | ja existiam: %d | nao cadastrados: %d "
                            + "| sem cadastro no TRC: %d | sem CPF: %d | empreiteiras sem card: %d "
                            + "| funcoes sem correspondente: %d | nivel gestor atribuido: %d | erros: %d",
                    cadastrados.size(), jaExistiam.size(), naoCadastrados.size(),
                    semCadastroNoTrc.size(), semCpf.size(), semCard.size(),
                    funcoesDesconhecidas.size(), atribuicoesGestor.size(), erros.size());
        }

        public String problemasTexto() {
            StringBuilder texto = new StringBuilder();

            for (String item : semCard) {
                texto.append("sem card: ").append(item).append("; ");
            }
            for (String item : semCadastroNoTrc) {
                texto.append("nao achou no TRC: ").append(item).append("; ");
            }
            for (String item : semCpf) {
                texto.append("sem cpf: ").append(item).append("; ");
            }
            for (String item : erros) {
                texto.append("erro: ").append(item).append("; ");
            }

            return texto.toString();
        }
    }
}