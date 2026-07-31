package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.domain.entity.ExecucaoLog;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class CronometroContexto {

    public record Fechamento(ExecucaoLog registro, RelatorioExecucaoService.Dados dados) {

        public Cronometro cronometro() {
            return dados == null ? null : dados.cronometro;
        }
    }

    private final ThreadLocal<Cronometro> atual = new ThreadLocal<>();
    private final List<Fechamento> rodada = Collections.synchronizedList(new ArrayList<>());

    public Cronometro iniciar(String codigoObra, String nomeObra) {
        Cronometro cronometro = new Cronometro(codigoObra, nomeObra);
        atual.set(cronometro);
        return cronometro;
    }

    public Cronometro atual() {
        return atual.get();
    }

    public void encerrar(ExecucaoLog registro, RelatorioExecucaoService.Dados dados) {
        Cronometro cronometro = atual.get();

        if (cronometro != null) {
            cronometro.encerrar();

            if (registro != null && registro.getStatus() != null) {
                cronometro.definirStatus(String.valueOf(registro.getStatus()));
            }
        }

        if (dados != null && dados.cronometro != null) {
            rodada.add(new Fechamento(registro, dados));
        }

        atual.remove();
    }

    public void limparRodada() {
        rodada.clear();
    }

    public List<Fechamento> daRodada() {
        synchronized (rodada) {
            return new ArrayList<>(rodada);
        }
    }
}