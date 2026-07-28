package com.cury.automacaocatraca.config;

import com.cury.automacaocatraca.orchestration.ExecucaoAgendada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.time.ZoneId;

@Configuration
@EnableScheduling
public class AgendamentoConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoConfig.class);

    private final ExecucaoAgendada execucao;
    private final String crons;
    private final String fuso;
    private final boolean ativo;

    public AgendamentoConfig(ExecucaoAgendada execucao,
                             @Value("${automacao.agendamento.crons:}") String crons,
                             @Value("${automacao.agendamento.fuso:America/Sao_Paulo}") String fuso,
                             @Value("${automacao.agendamento.ativo:true}") boolean ativo) {
        this.execucao = execucao;
        this.crons = crons == null ? "" : crons;
        this.fuso = fuso;
        this.ativo = ativo;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (!ativo) {
            log.warn("Agendamento DESLIGADO (automacao.agendamento.ativo=false)");
            return;
        }

        ZoneId zona = ZoneId.of(fuso);
        int registrados = 0;

        for (String bruto : crons.split(";")) {
            String cron = bruto.trim();

            if (cron.isEmpty() || "-".equals(cron)) {
                continue;
            }

            try {
                registrar.addTriggerTask(
                        () -> execucao.executarTodas("agendamento " + cron),
                        new CronTrigger(cron, zona));

                log.info("Agendamento registrado: '{}' ({})", cron, fuso);
                registrados++;

            } catch (Exception e) {
                log.error("Expressao cron invalida ignorada: '{}' — {}", cron, e.getMessage());
            }
        }

        if (registrados == 0) {
            log.warn("Nenhum horario valido em automacao.agendamento.crons — "
                    + "o robo so vai rodar com --agora ou --obra=CODIGO");
        }
    }
}