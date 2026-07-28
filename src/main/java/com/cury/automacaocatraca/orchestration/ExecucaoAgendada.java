package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.config.ObraConfig;
import com.cury.automacaocatraca.config.ObrasRegistry;
import com.cury.automacaocatraca.domain.entity.ExecucaoLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ExecucaoAgendada {

    private static final Logger log = LoggerFactory.getLogger(ExecucaoAgendada.class);

    private final ObrasRegistry obrasRegistry;
    private final AutomacaoPipeline pipeline;

    private final AtomicBoolean rodando = new AtomicBoolean(false);

    public ExecucaoAgendada(ObrasRegistry obrasRegistry, AutomacaoPipeline pipeline) {
        this.obrasRegistry = obrasRegistry;
        this.pipeline = pipeline;
    }

    public List<ExecucaoLog> executarTodas(String origem) {
        List<ExecucaoLog> resultados = new ArrayList<>();

        if (!rodando.compareAndSet(false, true)) {
            log.warn("Ja existe uma execucao em andamento — {} ignorado", origem);
            return resultados;
        }

        LocalDateTime inicio = LocalDateTime.now();

        try {
            List<ObraConfig> obras = obrasRegistry.obras();

            if (obras == null || obras.isEmpty()) {
                log.warn("Nenhuma obra configurada em automacao.obras");
                return resultados;
            }

            log.info("##########################################################");
            log.info("  RODADA INICIADA ({}) — {} obra(s)", origem, obras.size());
            log.info("##########################################################");

            for (ObraConfig obra : obras) {
                try {
                    resultados.add(pipeline.executar(obra));

                } catch (Exception e) {
                    if (desligando(e)) {

                        log.warn("Execucao interrompida durante {} — nao vou seguir para as proximas obras",
                                obra.codigo());
                        break;
                    }

                    log.error("Obra {} falhou fora do pipeline: {}", obra.codigo(), e.getMessage(), e);
                }
            }

            resumir(resultados, inicio);

        } finally {
            rodando.set(false);
        }

        return resultados;
    }

    private boolean desligando(Throwable erro) {
        for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
            String mensagem = atual.getMessage() == null ? "" : atual.getMessage();

            if (mensagem.contains("Shutdown in progress")
                    || mensagem.contains("EntityManagerFactory is closed")
                    || mensagem.contains("has been closed")) {
                return true;
            }
        }

        return false;
    }

    private void resumir(List<ExecucaoLog> resultados, LocalDateTime inicio) {
        long minutos = Duration.between(inicio, LocalDateTime.now()).toMinutes();

        log.info("##########################################################");
        log.info("  RODADA CONCLUIDA em {} min", minutos);

        for (ExecucaoLog resultado : resultados) {
            log.info("  {} — {} | empreiteiras: {} | funcionarios: {}",
                    resultado.getCodigoObra(),
                    resultado.getStatus(),
                    resultado.getEmpreiteirasCadastradas(),
                    resultado.getFuncionariosCadastrados());
        }

        log.info("##########################################################");
    }
}