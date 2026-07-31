package com.cury.automacaocatraca.orchestration;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Cronometro {

    public static final String ETAPA_TRC = "1. TRC (login, obra e download)";
    public static final String ETAPA_EXCEL = "2. Leitura da planilha";
    public static final String ETAPA_EMPREITEIRAS = "3. Cadastro de empreiteiras";
    public static final String ETAPA_VINCULOS = "4. Vinculo empreiteira x obra";
    public static final String ETAPA_FUNCIONARIOS = "5. Cadastro de funcionarios";
    public static final String ETAPA_IMPORTACAO = "6-7. Upload e importacao";
    public static final String ETAPA_COMPLETAR = "8. Completar cadastros";
    public static final String ETAPA_ENCERRAMENTO = "9. Fechamento";
    public static final String FORA_DAS_ETAPAS = "fora das etapas";

    private final String codigoObra;
    private final String nomeObra;
    private final long inicioMs;

    private long fimMs;
    private LocalDate dataReferencia = LocalDate.now();
    private String status = "";

    private final Deque<Marcacao> abertas = new ArrayDeque<>();
    private final List<Medicao> medicoes = new ArrayList<>();
    private final Map<String, UsoNavegador> usosNavegador = new LinkedHashMap<>();
    private final Map<String, Pausa> faixasDePausa = new LinkedHashMap<>();
    private final Map<String, Long> navegadorPorEtapa = new LinkedHashMap<>();
    private final Map<String, Long> pausaPorEtapa = new LinkedHashMap<>();

    private long totalNavegadorMs;
    private long totalPausaMs;
    private long buscasVazias;
    private long errosNavegador;

    public Cronometro(String codigoObra, String nomeObra) {
        this.codigoObra = codigoObra == null ? "" : codigoObra;
        this.nomeObra = nomeObra == null ? "" : nomeObra;
        this.inicioMs = System.currentTimeMillis();
    }

    public record Medicao(String etapa, String detalhe, long inicioMs, long duracaoMs, int nivel) {
    }

    public static final class Estatistica {
        public final String nome;
        public int vezes;
        public long totalMs;
        public long navegadorMs;
        public long pausaMs;
        public int itens;
        public long piorItemMs;
        public String piorItem = "";

        public Estatistica(String nome) {
            this.nome = nome;
        }

        public long mediaItemMs() {
            return itens == 0 ? 0 : totalMs / itens;
        }
    }

    public static final class UsoNavegador {
        public final String categoria;
        public long chamadas;
        public long totalMs;
        public long maiorMs;
        public long erros;
        public long vazias;
        public String piorAlvo = "";

        public UsoNavegador(String categoria) {
            this.categoria = categoria;
        }

        public long mediaMs() {
            return chamadas == 0 ? 0 : totalMs / chamadas;
        }
    }

    public static final class Pausa {
        public final String faixa;
        public final int ordem;
        public long vezes;
        public long totalMs;

        public Pausa(String faixa, int ordem) {
            this.faixa = faixa;
            this.ordem = ordem;
        }
    }

    public final class Marcacao implements AutoCloseable {
        private final String etapa;
        private final String detalhe;
        private final long comeco;
        private final int nivel;
        private boolean fechada;

        private Marcacao(String etapa, String detalhe, int nivel) {
            this.etapa = etapa;
            this.detalhe = detalhe;
            this.nivel = nivel;
            this.comeco = System.currentTimeMillis();
        }

        public String etapa() {
            return etapa;
        }

        public long parcialMs() {
            return System.currentTimeMillis() - comeco;
        }

        public void fechar() {
            if (fechada) {
                return;
            }

            fechada = true;
            abertas.remove(this);
            medicoes.add(new Medicao(etapa, detalhe, comeco, System.currentTimeMillis() - comeco, nivel));
        }

        @Override
        public void close() {
            fechar();
        }
    }

    public Marcacao iniciar(String etapa) {
        return iniciar(etapa, null);
    }

    public Marcacao iniciar(String etapa, String detalhe) {
        Marcacao marcacao = new Marcacao(etapa, detalhe, abertas.size());
        abertas.push(marcacao);
        return marcacao;
    }

    public void encerrar() {
        while (!abertas.isEmpty()) {
            abertas.peek().fechar();
        }

        if (fimMs == 0) {
            fimMs = System.currentTimeMillis();
        }
    }

    public String etapaAtual() {
        Marcacao raiz = abertas.peekLast();
        return raiz == null ? FORA_DAS_ETAPAS : raiz.etapa;
    }

    public synchronized void registrarChamada(String categoria, String alvo, long duracaoMs,
                                              boolean erro, boolean semResultado) {
        UsoNavegador uso = usosNavegador.computeIfAbsent(categoria, UsoNavegador::new);
        uso.chamadas++;
        uso.totalMs += duracaoMs;

        if (duracaoMs > uso.maiorMs) {
            uso.maiorMs = duracaoMs;
            uso.piorAlvo = alvo == null ? "" : alvo;
        }

        if (erro) {
            uso.erros++;
            errosNavegador++;
        }

        if (semResultado) {
            uso.vazias++;
            buscasVazias++;
        }

        totalNavegadorMs += duracaoMs;
        navegadorPorEtapa.merge(etapaAtual(), duracaoMs, Long::sum);
    }

    public synchronized void registrarPausa(long duracaoMs) {
        if (duracaoMs < 150 || duracaoMs > 300000) {
            return;
        }

        Pausa pausa = faixasDePausa.computeIfAbsent(faixa(duracaoMs), nome -> new Pausa(nome, ordem(duracaoMs)));
        pausa.vezes++;
        pausa.totalMs += duracaoMs;

        totalPausaMs += duracaoMs;
        pausaPorEtapa.merge(etapaAtual(), duracaoMs, Long::sum);
    }

    private String faixa(long millis) {
        if (millis < 500) {
            return "0,15 a 0,5s";
        }
        if (millis < 1000) {
            return "0,5 a 1s";
        }
        if (millis < 2000) {
            return "1 a 2s";
        }
        if (millis < 3000) {
            return "2 a 3s";
        }
        if (millis < 5000) {
            return "3 a 5s";
        }
        if (millis < 10000) {
            return "5 a 10s";
        }
        if (millis < 30000) {
            return "10 a 30s";
        }
        return "mais de 30s";
    }

    private int ordem(long millis) {
        if (millis < 500) {
            return 1;
        }
        if (millis < 1000) {
            return 2;
        }
        if (millis < 2000) {
            return 3;
        }
        if (millis < 3000) {
            return 4;
        }
        if (millis < 5000) {
            return 5;
        }
        if (millis < 10000) {
            return 6;
        }
        if (millis < 30000) {
            return 7;
        }
        return 8;
    }

    public List<Estatistica> etapas() {
        Map<String, Estatistica> mapa = new LinkedHashMap<>();

        for (Medicao medicao : medicoes) {
            if (medicao.nivel() != 0) {
                continue;
            }

            Estatistica estatistica = mapa.computeIfAbsent(medicao.etapa(), Estatistica::new);
            estatistica.vezes++;
            estatistica.totalMs += medicao.duracaoMs();
        }

        for (Estatistica estatistica : mapa.values()) {
            estatistica.navegadorMs = navegadorPorEtapa.getOrDefault(estatistica.nome, 0L);
            estatistica.pausaMs = pausaPorEtapa.getOrDefault(estatistica.nome, 0L);

            int nivelDetalhe = nivelMaisFundo(estatistica.nome);

            for (Medicao medicao : medicoes) {
                if (!medicao.etapa().equals(estatistica.nome) || medicao.nivel() != nivelDetalhe) {
                    continue;
                }

                estatistica.itens++;

                if (medicao.duracaoMs() > estatistica.piorItemMs) {
                    estatistica.piorItemMs = medicao.duracaoMs();
                    estatistica.piorItem = medicao.detalhe() == null ? "" : medicao.detalhe();
                }
            }
        }

        List<Estatistica> lista = new ArrayList<>(mapa.values());
        lista.sort(Comparator.comparingLong((Estatistica e) -> e.totalMs).reversed());

        return lista;
    }

    private int nivelMaisFundo(String etapa) {
        int nivel = 0;

        for (Medicao medicao : medicoes) {
            if (medicao.etapa().equals(etapa) && medicao.nivel() > nivel) {
                nivel = medicao.nivel();
            }
        }

        return nivel;
    }

    public List<Medicao> itensMaisLentos(int quantidade) {
        List<Medicao> lista = new ArrayList<>();

        for (Medicao medicao : medicoes) {
            if (medicao.nivel() > 0 && medicao.detalhe() != null) {
                lista.add(medicao);
            }
        }

        lista.sort(Comparator.comparingLong(Medicao::duracaoMs).reversed());

        return lista.size() > quantidade ? new ArrayList<>(lista.subList(0, quantidade)) : lista;
    }

    public List<Medicao> todasAsMedicoes() {
        return new ArrayList<>(medicoes);
    }

    public List<UsoNavegador> navegador() {
        List<UsoNavegador> lista = new ArrayList<>(usosNavegador.values());
        lista.sort(Comparator.comparingLong((UsoNavegador u) -> u.totalMs).reversed());
        return lista;
    }

    public List<Pausa> pausas() {
        List<Pausa> lista = new ArrayList<>(faixasDePausa.values());
        lista.sort(Comparator.comparingInt((Pausa p) -> p.ordem));
        return lista;
    }

    public long tempoDaEtapa(String etapa) {
        long total = 0;

        for (Medicao medicao : medicoes) {
            if (medicao.nivel() == 0 && medicao.etapa().equals(etapa)) {
                total += medicao.duracaoMs();
            }
        }

        return total;
    }

    public long totalMs() {
        return (fimMs == 0 ? System.currentTimeMillis() : fimMs) - inicioMs;
    }

    public long totalNavegadorMs() {
        return totalNavegadorMs;
    }

    public long totalPausaMs() {
        return totalPausaMs;
    }

    public long totalMedidoMs() {
        long total = 0;

        for (Medicao medicao : medicoes) {
            if (medicao.nivel() == 0) {
                total += medicao.duracaoMs();
            }
        }

        return total;
    }

    public long buscasVazias() {
        return buscasVazias;
    }

    public long errosNavegador() {
        return errosNavegador;
    }

    public long chamadasNavegador() {
        long total = 0;

        for (UsoNavegador uso : usosNavegador.values()) {
            total += uso.chamadas;
        }

        return total;
    }

    public String codigoObra() {
        return codigoObra;
    }

    public String nomeObra() {
        return nomeObra;
    }

    public long inicioMs() {
        return inicioMs;
    }

    public long fimMs() {
        return fimMs == 0 ? System.currentTimeMillis() : fimMs;
    }

    public LocalDate dataReferencia() {
        return dataReferencia;
    }

    public void definirDataReferencia(LocalDate data) {
        if (data != null) {
            this.dataReferencia = data;
        }
    }

    public String status() {
        return status;
    }

    public void definirStatus(String status) {
        this.status = status == null ? "" : status;
    }

    public static String formatar(long millis) {
        long valor = Math.max(0, millis);
        long segundos = valor / 1000;
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        long resto = segundos % 60;

        if (horas > 0) {
            return String.format("%dh %02dm %02ds", horas, minutos, resto);
        }
        if (minutos > 0) {
            return String.format("%dm %02ds", minutos, resto);
        }
        if (segundos > 0) {
            return String.format("%d,%01ds", segundos, (valor % 1000) / 100);
        }

        return valor + "ms";
    }

    public static String percentual(long parte, long total) {
        if (total <= 0) {
            return "0%";
        }

        return Math.round(parte * 100.0 / total) + "%";
    }
}