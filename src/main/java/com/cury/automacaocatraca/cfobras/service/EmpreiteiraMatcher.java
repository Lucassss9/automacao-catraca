package com.cury.automacaocatraca.cfobras.service;

import com.cury.automacaocatraca.cfobras.dto.PendenciaFuncionario;
import com.cury.automacaocatraca.cfobras.dto.PlanoConciliacao;
import com.cury.automacaocatraca.domain.entity.EmpreiteiraCache;
import com.cury.automacaocatraca.domain.entity.FuncionarioCache;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import com.cury.automacaocatraca.excel.RelatorioFrequencia;
import com.cury.automacaocatraca.repository.EmpreiteiraCacheRepository;
import com.cury.automacaocatraca.repository.FuncionarioCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class EmpreiteiraMatcher {

    private static final Logger log = LoggerFactory.getLogger(EmpreiteiraMatcher.class);

    private final EmpreiteiraCacheRepository empreiteiraRepository;
    private final FuncionarioCacheRepository funcionarioRepository;

    public EmpreiteiraMatcher(EmpreiteiraCacheRepository empreiteiraRepository,
                              FuncionarioCacheRepository funcionarioRepository) {
        this.empreiteiraRepository = empreiteiraRepository;
        this.funcionarioRepository = funcionarioRepository;
    }

    public PlanoConciliacao conciliar(RelatorioFrequencia relatorio) {
        List<String> existentes = new ArrayList<>();
        List<String> paraCadastrar = new ArrayList<>();
        List<PendenciaFuncionario> funcionariosPendentes = new ArrayList<>();

        for (String empreiteiraTrc : relatorio.empreiteiras()) {
            String empreiteiraNormalizada = NormalizadorNome.normalizar(empreiteiraTrc);

            if (empreiteiraCadastrada(empreiteiraNormalizada)) {
                existentes.add(empreiteiraTrc);
            } else {
                paraCadastrar.add(empreiteiraTrc);
            }

            for (String funcionarioTrc : relatorio.funcionariosDa(empreiteiraTrc)) {
                if (!funcionarioCadastrado(funcionarioTrc, empreiteiraNormalizada)) {
                    funcionariosPendentes.add(new PendenciaFuncionario(funcionarioTrc, empreiteiraTrc));
                }
            }
        }

        PlanoConciliacao plano = new PlanoConciliacao(
                relatorio.nomeObra(), existentes, paraCadastrar, funcionariosPendentes);

        log.info("Conciliacao: {} empreiteiras ja cadastradas, {} a cadastrar, {} funcionarios a cadastrar",
                existentes.size(), paraCadastrar.size(), funcionariosPendentes.size());

        return plano;
    }

    private boolean empreiteiraCadastrada(String nomeNormalizado) {
        Optional<EmpreiteiraCache> registro = empreiteiraRepository.findByNomeNormalizado(nomeNormalizado);
        return registro.isPresent() && registro.get().isCadastradaNoCfObras();
    }

    private boolean funcionarioCadastrado(String nomeFuncionarioTrc, String empreiteiraNormalizada) {
        String nomeNormalizado = NormalizadorNome.normalizar(nomeFuncionarioTrc);

        Optional<FuncionarioCache> registro = funcionarioRepository
                .findByNomeNormalizadoAndEmpreiteiraNomeNormalizado(nomeNormalizado, empreiteiraNormalizada);

        return registro.isPresent() && registro.get().isCadastradoNoCfObras();
    }
}