package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.config.ObrasRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ExecucaoManualRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExecucaoManualRunner.class);

    private final ObrasRegistry obrasRegistry;
    private final AutomacaoPipeline pipeline;
    private final ExecucaoAgendada agendada;
    private final CronometroContexto cronometroContexto;
    private final RelatorioGeralService relatorioGeralService;
    private final SessaoNavegadores sessao;

    public ExecucaoManualRunner(ObrasRegistry obrasRegistry,
                                AutomacaoPipeline pipeline,
                                ExecucaoAgendada agendada,
                                CronometroContexto cronometroContexto,
                                RelatorioGeralService relatorioGeralService,
                                SessaoNavegadores sessao) {
        this.obrasRegistry = obrasRegistry;
        this.pipeline = pipeline;
        this.agendada = agendada;
        this.cronometroContexto = cronometroContexto;
        this.relatorioGeralService = relatorioGeralService;
        this.sessao = sessao;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("obra")) {
            List<String> valores = args.getOptionValues("obra");

            if (valores == null || valores.isEmpty()) {
                log.warn("--obra sem valor. Use --obra=CODIGO");
                return;
            }

            String codigo = valores.get(0);
            log.info("Execucao manual pedida para a obra {}", codigo);

            LocalDateTime inicio = LocalDateTime.now();
            cronometroContexto.limparRodada();

            try {
                pipeline.executar(obrasRegistry.porCodigo(codigo));
                relatorioGeralService.gerar("--obra=" + codigo, inicio, cronometroContexto.daRodada());
            } catch (IllegalArgumentException e) {
                log.error("{}", e.getMessage());
            } finally {
                sessao.fechar();
            }

            return;
        }

        if (args.containsOption("agora")) {
            log.info("Execucao manual pedida para todas as obras");
            agendada.executarTodas("--agora");
            return;
        }

        log.info("Nenhum disparo manual. Aguardando o agendamento — "
                + "use --agora ou --obra=CODIGO para rodar na hora.");
    }
}