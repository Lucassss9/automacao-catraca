package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.config.ObraConfig;
import com.cury.automacaocatraca.config.ObrasRegistry;
import com.cury.automacaocatraca.domain.entity.ExecucaoLog;
import com.cury.automacaocatraca.domain.enums.StatusExecucao;
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
    private final CronometroContexto cronometroContexto;
    private final RelatorioGeralService relatorioGeralService;
    private final SessaoNavegadores sessao;

    private static final int TENTATIVAS_POR_OBRA = 3;

    private final AtomicBoolean rodando = new AtomicBoolean(false);

    public ExecucaoAgendada(ObrasRegistry obrasRegistry,
                            AutomacaoPipeline pipeline,
                            CronometroContexto cronometroContexto,
                            RelatorioGeralService relatorioGeralService,
                            SessaoNavegadores sessao) {
        this.obrasRegistry = obrasRegistry;
        this.pipeline = pipeline;
        this.cronometroContexto = cronometroContexto;
        this.relatorioGeralService = relatorioGeralService;
        this.sessao = sessao;
    }

    public List<ExecucaoLog> executarTodas(String origem) {
        List<ExecucaoLog> resultados = new ArrayList<>();

        if (!rodando.compareAndSet(false, true)) {
            log.warn("Ja existe uma execucao em andamento — {} ignorado", origem);
            return resultados;
        }

        LocalDateTime inicio = LocalDateTime.now();
        cronometroContexto.limparRodada();

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
                if (!processarObraComRetentativa(obra, resultados)) {
                    break;
                }
            }

            resumir(resultados, inicio);
            relatorioGeralService.gerar(origem, inicio, cronometroContexto.daRodada());

        } finally {
            sessao.fechar();
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

    private boolean processarObraComRetentativa(ObraConfig obra, List<ExecucaoLog> resultados) {
        for (int tentativa = 1; tentativa <= TENTATIVAS_POR_OBRA; tentativa++) {
            boolean ultima = tentativa == TENTATIVAS_POR_OBRA;

            try {
                ExecucaoLog resultado = pipeline.executar(obra);

                if (resultado.getStatus() != StatusExecucao.FALHA || ultima) {
                    resultados.add(resultado);
                    return true;
                }

                log.warn("Obra {} terminou em FALHA na tentativa {} de {} — fechando os navegadores e tentando de novo",
                        obra.codigo(), tentativa, TENTATIVAS_POR_OBRA);
                sessao.descartarTudo();

            } catch (Exception e) {
                if (desligando(e)) {
                    log.warn("Execucao interrompida durante {} — nao vou seguir para as proximas obras",
                            obra.codigo());
                    return false;
                }

                if (ultima) {
                    log.error("Obra {} falhou fora do pipeline apos {} tentativas: {}",
                            obra.codigo(), TENTATIVAS_POR_OBRA, e.getMessage(), e);
                    return true;
                }

                log.warn("Obra {} falhou fora do pipeline na tentativa {} de {} — fechando os navegadores e tentando de novo: {}",
                        obra.codigo(), tentativa, TENTATIVAS_POR_OBRA, e.getMessage());
                sessao.descartarTudo();
            }
        }

        return true;
    }

    private void resumir(List<ExecucaoLog> resultados, LocalDateTime inicio) {
        long millis = Duration.between(inicio, LocalDateTime.now()).toMillis();

        log.info("##########################################################");
        log.info("  RODADA CONCLUIDA em {}", Cronometro.formatar(millis));

        for (ExecucaoLog resultado : resultados) {
            log.info("  {} — {} | empreiteiras: {} | funcionarios: {} | tempo: {}",
                    resultado.getCodigoObra(),
                    resultado.getStatus(),
                    resultado.getEmpreiteirasCadastradas(),
                    resultado.getFuncionariosCadastrados(),
                    tempoDa(resultado.getCodigoObra()));
        }

        log.info("##########################################################");
    }

    private String tempoDa(String codigoObra) {
        for (CronometroContexto.Fechamento fechamento : cronometroContexto.daRodada()) {
            Cronometro cronometro = fechamento.cronometro();

            if (cronometro != null && cronometro.codigoObra().equals(codigoObra)) {
                return Cronometro.formatar(cronometro.totalMs());
            }
        }

        return "-";
    }
}