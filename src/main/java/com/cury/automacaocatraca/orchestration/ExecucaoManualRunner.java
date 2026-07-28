package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.config.ObrasRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ExecucaoManualRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExecucaoManualRunner.class);

    private final ObrasRegistry obrasRegistry;
    private final AutomacaoPipeline pipeline;
    private final ExecucaoAgendada agendada;

    public ExecucaoManualRunner(ObrasRegistry obrasRegistry,
                                AutomacaoPipeline pipeline,
                                ExecucaoAgendada agendada) {
        this.obrasRegistry = obrasRegistry;
        this.pipeline = pipeline;
        this.agendada = agendada;
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

            try {
                pipeline.executar(obrasRegistry.porCodigo(codigo));
            } catch (IllegalArgumentException e) {
                log.error("{}", e.getMessage());
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