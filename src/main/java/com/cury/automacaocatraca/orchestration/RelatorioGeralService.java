package com.cury.automacaocatraca.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RelatorioGeralService {

    private static final Logger log = LoggerFactory.getLogger(RelatorioGeralService.class);

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LEITURA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Path RAIZ = Path.of("relatorios");

    private final AnalisadorDeTempos analisador;

    public RelatorioGeralService(AnalisadorDeTempos analisador) {
        this.analisador = analisador;
    }

    public Optional<Path> gerar(String origem, LocalDateTime inicio,
                                List<CronometroContexto.Fechamento> fechamentos) {
        if (fechamentos == null || fechamentos.isEmpty()) {
            log.info("Rodada sem obras medidas — relatorio geral nao gerado");
            return Optional.empty();
        }

        try {
            LocalDate data = dataDaRodada(fechamentos);
            Path pasta = RAIZ.resolve(data.format(DIA));

            Files.createDirectories(pasta);

            Path destino = pasta.resolve("relatorio-geral.md");
            Files.writeString(destino, montar(origem, inicio, data, fechamentos), StandardCharsets.UTF_8);

            log.info("Relatorio geral gravado em {}", destino.toAbsolutePath());

            return Optional.of(destino);

        } catch (Exception e) {
            log.warn("Nao consegui gravar o relatorio geral: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private LocalDate dataDaRodada(List<CronometroContexto.Fechamento> fechamentos) {
        for (CronometroContexto.Fechamento fechamento : fechamentos) {
            if (fechamento.dados() != null && fechamento.dados().dataReferencia != null) {
                return fechamento.dados().dataReferencia;
            }
        }

        return LocalDate.now();
    }

    private String montar(String origem, LocalDateTime inicio, LocalDate data,
                          List<CronometroContexto.Fechamento> fechamentos) {

        StringBuilder texto = new StringBuilder();
        LocalDateTime fim = LocalDateTime.now();

        long somaTempo = 0;
        long somaNavegador = 0;
        long somaPausa = 0;
        long somaBuscasVazias = 0;
        int totalFuncionariosRelatorio = 0;
        int totalFuncionariosCadastrados = 0;
        int totalEmpreiteirasCadastradas = 0;
        int totalPendencias = 0;

        for (CronometroContexto.Fechamento fechamento : fechamentos) {
            Cronometro cronometro = fechamento.cronometro();
            RelatorioExecucaoService.Dados dados = fechamento.dados();

            if (cronometro != null) {
                somaTempo += cronometro.totalMs();
                somaNavegador += cronometro.totalNavegadorMs();
                somaPausa += cronometro.totalPausaMs();
                somaBuscasVazias += cronometro.buscasVazias();
            }

            if (dados != null) {
                totalFuncionariosRelatorio += dados.funcionariosNoRelatorio;
                totalFuncionariosCadastrados += dados.funcionarios.cadastrados.size();
                totalEmpreiteirasCadastradas += dados.empreiteirasCadastradas.size();
                totalPendencias += dados.pendencias.size() + dados.funcionarios.erros.size();
            }
        }

        long duracaoRodada = java.time.Duration.between(inicio, fim).toMillis();

        texto.append("# Relatorio geral da rodada\n\n");
        texto.append("Dados de ").append(data.format(DIA)).append(" · disparo `").append(origem).append("`\n\n");
        texto.append("Comecou ").append(inicio.format(LEITURA))
                .append(" e terminou ").append(fim.format(HORA))
                .append(" — **").append(Cronometro.formatar(duracaoRodada)).append("** de relogio, ")
                .append(fechamentos.size()).append(" obra(s).\n\n");

        texto.append("| | |\n|---|---:|\n");
        texto.append("| Tempo somado das obras | ").append(Cronometro.formatar(somaTempo)).append(" |\n");
        texto.append("| Agindo no navegador | ").append(Cronometro.formatar(somaNavegador))
                .append(" (").append(Cronometro.percentual(somaNavegador, somaTempo)).append(") |\n");
        texto.append("| Parado esperando | ").append(Cronometro.formatar(somaPausa))
                .append(" (").append(Cronometro.percentual(somaPausa, somaTempo)).append(") |\n");
        texto.append("| Funcionarios no relatorio | ").append(totalFuncionariosRelatorio).append(" |\n");
        texto.append("| Funcionarios cadastrados | ").append(totalFuncionariosCadastrados).append(" |\n");
        texto.append("| Empreiteiras cadastradas | ").append(totalEmpreiteirasCadastradas).append(" |\n");
        texto.append("| Buscas de elemento sem resultado | ").append(somaBuscasVazias).append(" |\n");
        texto.append("| Itens pendentes | ").append(totalPendencias).append(" |\n\n");

        placar(texto, fechamentos, somaTempo);
        etapasConsolidadas(texto, fechamentos, somaTempo);
        pontosMaisLentos(texto, fechamentos);
        navegadorConsolidado(texto, fechamentos);
        pausasConsolidadas(texto, fechamentos);
        pendencias(texto, fechamentos);

        List<String> achados = analisador.analisarRodada(fechamentos);

        if (!achados.isEmpty()) {
            texto.append("---\n\n## Leitura da rodada\n\n");

            for (String achado : achados) {
                texto.append("- ").append(achado).append("\n");
            }

            texto.append("\n");
        }

        texto.append("---\n\n## Onde da para ganhar tempo, obra por obra\n\n");

        for (CronometroContexto.Fechamento fechamento : ordenadosPorTempo(fechamentos)) {
            Cronometro cronometro = fechamento.cronometro();

            if (cronometro == null) {
                continue;
            }

            List<String> porObra = analisador.analisarObra(cronometro);

            if (porObra.isEmpty()) {
                continue;
            }

            texto.append("### ").append(cronometro.codigoObra()).append("\n\n");

            for (String achado : porObra) {
                texto.append("- ").append(achado).append("\n");
            }

            texto.append("\n");
        }

        texto.append("---\n\nOs relatorios detalhados de cada obra estao nas subpastas deste mesmo dia.\n");

        return texto.toString();
    }

    private List<CronometroContexto.Fechamento> ordenadosPorTempo(
            List<CronometroContexto.Fechamento> fechamentos) {

        List<CronometroContexto.Fechamento> lista = new ArrayList<>(fechamentos);

        lista.sort(Comparator.comparingLong(
                        (CronometroContexto.Fechamento f) -> f.cronometro() == null ? 0 : f.cronometro().totalMs())
                .reversed());

        return lista;
    }

    private void placar(StringBuilder texto, List<CronometroContexto.Fechamento> fechamentos, long somaTempo) {
        texto.append("---\n\n## Placar das obras\n\n");
        texto.append("| Obra | Tempo | % | Status | Func. no relatorio | Cadastrados | Tempo/func. | Pendencias |\n");
        texto.append("|---|---:|---:|---|---:|---:|---:|---:|\n");

        for (CronometroContexto.Fechamento fechamento : ordenadosPorTempo(fechamentos)) {
            Cronometro cronometro = fechamento.cronometro();
            RelatorioExecucaoService.Dados dados = fechamento.dados();

            if (cronometro == null || dados == null) {
                continue;
            }

            long tempo = cronometro.totalMs();
            int funcionarios = dados.funcionariosNoRelatorio;
            String porFuncionario = funcionarios > 0 ? Cronometro.formatar(tempo / funcionarios) : "-";

            texto.append("| ").append(cronometro.codigoObra())
                    .append(" | ").append(Cronometro.formatar(tempo))
                    .append(" | ").append(Cronometro.percentual(tempo, somaTempo))
                    .append(" | ").append(cronometro.status().isBlank() ? "-" : cronometro.status())
                    .append(" | ").append(funcionarios)
                    .append(" | ").append(dados.funcionarios.cadastrados.size())
                    .append(" | ").append(porFuncionario)
                    .append(" | ").append(dados.pendencias.size() + dados.funcionarios.erros.size())
                    .append(" |\n");
        }

        texto.append("\n");
    }

    private void etapasConsolidadas(StringBuilder texto, List<CronometroContexto.Fechamento> fechamentos,
                                    long somaTempo) {

        Map<String, long[]> somas = new LinkedHashMap<>();
        Map<String, String> piorObra = new LinkedHashMap<>();
        Map<String, Long> piorTempo = new LinkedHashMap<>();

        for (CronometroContexto.Fechamento fechamento : fechamentos) {
            Cronometro cronometro = fechamento.cronometro();

            if (cronometro == null) {
                continue;
            }

            for (Cronometro.Estatistica etapa : cronometro.etapas()) {
                long[] acumulado = somas.computeIfAbsent(etapa.nome, nome -> new long[4]);
                acumulado[0] += etapa.totalMs;
                acumulado[1] += etapa.navegadorMs;
                acumulado[2] += etapa.pausaMs;
                acumulado[3] += etapa.itens;

                if (etapa.totalMs > piorTempo.getOrDefault(etapa.nome, 0L)) {
                    piorTempo.put(etapa.nome, etapa.totalMs);
                    piorObra.put(etapa.nome, cronometro.codigoObra());
                }
            }
        }

        if (somas.isEmpty()) {
            return;
        }

        List<Map.Entry<String, long[]>> lista = new ArrayList<>(somas.entrySet());
        lista.sort(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0]).reversed());

        texto.append("---\n\n## Tempo por etapa, somando as obras\n\n");
        texto.append("| Etapa | Tempo somado | % | Itens | Navegador | Parado | Obra que mais sofreu |\n");
        texto.append("|---|---:|---:|---:|---:|---:|---|\n");

        for (Map.Entry<String, long[]> item : lista) {
            long[] valores = item.getValue();

            texto.append("| ").append(item.getKey())
                    .append(" | ").append(Cronometro.formatar(valores[0]))
                    .append(" | ").append(Cronometro.percentual(valores[0], somaTempo))
                    .append(" | ").append(valores[3])
                    .append(" | ").append(Cronometro.formatar(valores[1]))
                    .append(" | ").append(Cronometro.formatar(valores[2]))
                    .append(" | ").append(piorObra.getOrDefault(item.getKey(), "-"))
                    .append(" (").append(Cronometro.formatar(piorTempo.getOrDefault(item.getKey(), 0L)))
                    .append(") |\n");
        }

        texto.append("\n");
    }

    private void pontosMaisLentos(StringBuilder texto, List<CronometroContexto.Fechamento> fechamentos) {
        record Ponto(String obra, String etapa, String item, long duracao) {
        }

        List<Ponto> pontos = new ArrayList<>();

        for (CronometroContexto.Fechamento fechamento : fechamentos) {
            Cronometro cronometro = fechamento.cronometro();

            if (cronometro == null) {
                continue;
            }

            for (Cronometro.Medicao medicao : cronometro.itensMaisLentos(20)) {
                pontos.add(new Ponto(cronometro.codigoObra(), medicao.etapa(),
                        medicao.detalhe(), medicao.duracaoMs()));
            }
        }

        if (pontos.isEmpty()) {
            return;
        }

        pontos.sort(Comparator.comparingLong(Ponto::duracao).reversed());

        texto.append("---\n\n## Os 20 pontos mais demorados da rodada\n\n");
        texto.append("| Tempo | Obra | Etapa | Item |\n|---:|---|---|---|\n");

        int limite = Math.min(20, pontos.size());

        for (int indice = 0; indice < limite; indice++) {
            Ponto ponto = pontos.get(indice);

            texto.append("| ").append(Cronometro.formatar(ponto.duracao()))
                    .append(" | ").append(ponto.obra())
                    .append(" | ").append(ponto.etapa())
                    .append(" | ").append(ponto.item())
                    .append(" |\n");
        }

        texto.append("\n");
    }

    private void navegadorConsolidado(StringBuilder texto, List<CronometroContexto.Fechamento> fechamentos) {
        Map<String, Cronometro.UsoNavegador> somas = new LinkedHashMap<>();

        for (CronometroContexto.Fechamento fechamento : fechamentos) {
            Cronometro cronometro = fechamento.cronometro();

            if (cronometro == null) {
                continue;
            }

            for (Cronometro.UsoNavegador uso : cronometro.navegador()) {
                Cronometro.UsoNavegador acumulado =
                        somas.computeIfAbsent(uso.categoria, Cronometro.UsoNavegador::new);

                acumulado.chamadas += uso.chamadas;
                acumulado.totalMs += uso.totalMs;
                acumulado.erros += uso.erros;
                acumulado.vazias += uso.vazias;

                if (uso.maiorMs > acumulado.maiorMs) {
                    acumulado.maiorMs = uso.maiorMs;
                    acumulado.piorAlvo = uso.piorAlvo;
                }
            }
        }

        if (somas.isEmpty()) {
            return;
        }

        List<Cronometro.UsoNavegador> lista = new ArrayList<>(somas.values());
        lista.sort(Comparator.comparingLong((Cronometro.UsoNavegador u) -> u.totalMs).reversed());

        texto.append("---\n\n## O que o robo pediu ao navegador na rodada\n\n");
        texto.append("| Acao | Chamadas | Tempo | Media | Pior chamada | Sem resultado | Excecoes |\n");
        texto.append("|---|---:|---:|---:|---:|---:|---:|\n");

        for (Cronometro.UsoNavegador uso : lista) {
            texto.append("| ").append(uso.categoria)
                    .append(" | ").append(uso.chamadas)
                    .append(" | ").append(Cronometro.formatar(uso.totalMs))
                    .append(" | ").append(Cronometro.formatar(uso.mediaMs()))
                    .append(" | ").append(Cronometro.formatar(uso.maiorMs))
                    .append(" | ").append(uso.vazias)
                    .append(" | ").append(uso.erros)
                    .append(" |\n");
        }

        texto.append("\n");
    }

    private void pausasConsolidadas(StringBuilder texto, List<CronometroContexto.Fechamento> fechamentos) {
        Map<String, Cronometro.Pausa> somas = new LinkedHashMap<>();

        for (CronometroContexto.Fechamento fechamento : fechamentos) {
            Cronometro cronometro = fechamento.cronometro();

            if (cronometro == null) {
                continue;
            }

            for (Cronometro.Pausa pausa : cronometro.pausas()) {
                Cronometro.Pausa acumulada = somas.computeIfAbsent(pausa.faixa,
                        faixa -> new Cronometro.Pausa(faixa, pausa.ordem));

                acumulada.vezes += pausa.vezes;
                acumulada.totalMs += pausa.totalMs;
            }
        }

        if (somas.isEmpty()) {
            return;
        }

        List<Cronometro.Pausa> lista = new ArrayList<>(somas.values());
        lista.sort(Comparator.comparingInt((Cronometro.Pausa p) -> p.ordem));

        texto.append("---\n\n## Paradas do robo na rodada\n\n");
        texto.append("| Faixa | Vezes | Tempo somado |\n|---|---:|---:|\n");

        for (Cronometro.Pausa pausa : lista) {
            texto.append("| ").append(pausa.faixa)
                    .append(" | ").append(pausa.vezes)
                    .append(" | ").append(Cronometro.formatar(pausa.totalMs))
                    .append(" |\n");
        }

        texto.append("\n");
    }

    private void pendencias(StringBuilder texto, List<CronometroContexto.Fechamento> fechamentos) {
        List<CronometroContexto.Fechamento> comDados = new ArrayList<>();

        for (CronometroContexto.Fechamento fechamento : fechamentos) {
            if (fechamento.dados() != null) {
                comDados.add(fechamento);
            }
        }

        if (comDados.isEmpty()) {
            return;
        }

        Set<String> globais = new LinkedHashSet<>(comDados.get(0).dados().pendencias);

        for (CronometroContexto.Fechamento fechamento : comDados) {
            globais.retainAll(new LinkedHashSet<>(fechamento.dados().pendencias));
        }

        texto.append("---\n\n## Pendencias da rodada\n\n");

        if (comDados.size() > 1 && !globais.isEmpty()) {
            texto.append("### Globais — aparecem em todas as obras (").append(globais.size()).append(")\n\n");
            texto.append("Sao do cadastro, nao da obra: a matriz de vinculos e a mesma para todo mundo, ")
                    .append("por isso elas se repetem em cada relatorio.\n\n");

            for (String pendencia : globais) {
                texto.append("- ").append(pendencia).append("\n");
            }

            texto.append("\n");
        }

        for (CronometroContexto.Fechamento fechamento : comDados) {
            RelatorioExecucaoService.Dados dados = fechamento.dados();
            List<String> proprias = new ArrayList<>();

            for (String pendencia : dados.pendencias) {
                if (comDados.size() == 1 || !globais.contains(pendencia)) {
                    proprias.add(pendencia);
                }
            }

            proprias.addAll(dados.funcionarios.erros);

            if (proprias.isEmpty()) {
                continue;
            }

            texto.append("### ").append(dados.codigoObra).append(" (").append(proprias.size()).append(")\n\n");

            for (String pendencia : proprias) {
                texto.append("- ").append(pendencia).append("\n");
            }

            texto.append("\n");
        }
    }
}