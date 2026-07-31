package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.domain.entity.ExecucaoLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class RelatorioExecucaoService {

    private static final Logger log = LoggerFactory.getLogger(RelatorioExecucaoService.class);

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LEITURA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Path RAIZ = Path.of("relatorios");
    private static final Path DOWNLOADS = Path.of("downloads");

    private final int retencaoDias;
    private final AnalisadorDeTempos analisador;

    public RelatorioExecucaoService(@Value("${automacao.retencao-dias:30}") int retencaoDias,
                                    AnalisadorDeTempos analisador) {
        this.retencaoDias = retencaoDias;
        this.analisador = analisador;
    }

    public static final class Dados {
        public LocalDateTime inicio = LocalDateTime.now();
        public LocalDate dataReferencia = LocalDate.now();
        public LocalDateTime fim = LocalDateTime.now();
        public String nomeObra = "";
        public String codigoObra = "";
        public String arquivoRelatorio = "";
        public Path arquivoOrigem;
        public int empreiteirasNoRelatorio;
        public int funcionariosNoRelatorio;
        public Cronometro cronometro;
        public final List<String> empreiteirasCadastradas = new ArrayList<>();
        public final List<String> vinculosCriados = new ArrayList<>();
        public final Map<String, List<String>> obrasPorEmpreiteira = new LinkedHashMap<>();
        public final List<String> pendencias = new ArrayList<>();
        public ConciliacaoFuncionariosStep.Resumo funcionarios = new ConciliacaoFuncionariosStep.Resumo();
    }

    public Optional<Path> gerar(ExecucaoLog registro, Dados dados) {
        try {
            Path pasta = RAIZ
                    .resolve(dados.dataReferencia.format(DIA))
                    .resolve(dados.codigoObra.isBlank() ? "SEM-OBRA" : dados.codigoObra);

            Files.createDirectories(pasta);

            Path destino = pasta.resolve("relatorio.md");
            Files.writeString(destino, montar(registro, dados), StandardCharsets.UTF_8);

            arquivarPlanilha(pasta, dados);
            limparDownloads(dados);
            apagarAntigos();

            log.info("Relatorio gravado em {}", destino.toAbsolutePath());

            return Optional.of(destino);

        } catch (Exception e) {
            log.warn("Nao consegui gravar o relatorio: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void arquivarPlanilha(Path pasta, Dados dados) {
        if (dados.arquivoOrigem == null || !Files.exists(dados.arquivoOrigem)) {
            return;
        }

        try {
            Path destino = pasta.resolve(dados.arquivoOrigem.getFileName().toString());
            Files.move(dados.arquivoOrigem, destino, StandardCopyOption.REPLACE_EXISTING);
            log.info("Planilha arquivada em {}", destino);
        } catch (IOException e) {
            log.warn("Nao consegui arquivar a planilha: {}", e.getMessage());
        }
    }

    private void limparDownloads(Dados dados) {
        if (!Files.isDirectory(DOWNLOADS)) {
            return;
        }

        try (Stream<Path> arquivos = Files.list(DOWNLOADS)) {
            int apagados = 0;

            for (Path arquivo : arquivos.filter(Files::isRegularFile).toList()) {
                if (dados.arquivoOrigem != null
                        && arquivo.toAbsolutePath().equals(dados.arquivoOrigem.toAbsolutePath())) {
                    continue;
                }

                try {
                    Files.delete(arquivo);
                    apagados++;
                } catch (IOException ignorado) {

                }
            }

            if (apagados > 0) {
                log.info("Downloads limpos: {} arquivo(s) removido(s)", apagados);
            }

        } catch (IOException e) {
            log.warn("Nao consegui limpar downloads/: {}", e.getMessage());
        }
    }

    private void apagarAntigos() {
        if (retencaoDias <= 0 || !Files.isDirectory(RAIZ)) {
            return;
        }

        LocalDate corte = LocalDate.now().minusDays(retencaoDias);

        try (Stream<Path> dias = Files.list(RAIZ)) {
            for (Path dia : dias.filter(Files::isDirectory).toList()) {
                LocalDate data = comoData(dia.getFileName().toString());

                if (data == null || !data.isBefore(corte)) {
                    continue;
                }

                apagarRecursivo(dia);
                log.info("Relatorio antigo removido: {}", dia.getFileName());
            }
        } catch (IOException e) {
            log.warn("Nao consegui limpar relatorios antigos: {}", e.getMessage());
        }
    }

    private LocalDate comoData(String nome) {
        try {
            return LocalDate.parse(nome, DIA);
        } catch (Exception e) {
            return null;
        }
    }

    private void apagarRecursivo(Path pasta) throws IOException {
        try (Stream<Path> itens = Files.walk(pasta)) {
            for (Path item : itens.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private String montar(ExecucaoLog registro, Dados dados) {
        StringBuilder texto = new StringBuilder();
        ConciliacaoFuncionariosStep.Resumo func = dados.funcionarios;

        long duracaoMs = dados.cronometro != null
                ? dados.cronometro.totalMs()
                : Duration.between(dados.inicio, dados.fim).toMillis();

        texto.append("# ").append(dados.nomeObra).append("\n\n");
        texto.append("Dados de ").append(dados.dataReferencia.format(DIA)).append("\n\n");
        texto.append("Rodou em ").append(dados.inicio.format(LEITURA))
                .append(" ate ").append(dados.fim.format(HORA))
                .append("  (").append(Cronometro.formatar(duracaoMs)).append(")  —  **")
                .append(registro.getStatus()).append("**\n\n");

        int atencao = func.funcoesDesconhecidas.size() + func.atribuicoesGestor.size()
                + func.erros.size() + dados.pendencias.size();

        if (atencao == 0) {
            texto.append("Nada pendente. Execucao limpa.\n\n");
        } else {
            texto.append("**").append(atencao).append(" item(ns) precisam da sua atencao** ")
                    .append("— veja a secao logo abaixo.\n\n");
        }

        texto.append("---\n\n## Precisa da sua atencao\n\n");

        boolean algo = false;
        algo |= secao(texto, "Erros", func.erros);
        algo |= secao(texto, "Pendencias", dados.pendencias);
        algo |= secao(texto, "Funcoes do TRC sem correspondente (foram para 'Outros')",
                func.funcoesDesconhecidas);
        algo |= secao(texto, "Nivel Gestor/Proprietario atribuido automaticamente",
                func.atribuicoesGestor);

        if (!algo) {
            texto.append("Nada.\n\n");
        }

        cronometro(texto, dados);

        texto.append("---\n\n## Resumo\n\n");
        texto.append("| | |\n|---|---:|\n");
        linha(texto, "Empreiteiras no relatorio", dados.empreiteirasNoRelatorio);
        linha(texto, "Empreiteiras cadastradas agora", dados.empreiteirasCadastradas.size());
        linha(texto, "Vinculos com a obra criados agora", dados.vinculosCriados.size());
        linha(texto, "Funcionarios no relatorio", dados.funcionariosNoRelatorio);
        linha(texto, "Funcionarios cadastrados agora", func.cadastrados.size());
        linha(texto, "Funcionarios que ja existiam", func.jaExistiam.size());
        linha(texto, "Funcionarios NAO cadastrados", func.naoCadastrados.size());

        texto.append("\n---\n\n## Empreiteiras\n\n");
        secao(texto, "Cadastradas nesta execucao", dados.empreiteirasCadastradas);
        secao(texto, "Vinculadas a obra nesta execucao", dados.vinculosCriados);

        if (!dados.obrasPorEmpreiteira.isEmpty()) {
            texto.append("### Obras de cada empreiteira (")
                    .append(dados.obrasPorEmpreiteira.size()).append(")\n\n");
            texto.append("| Empreiteira | Obras |\n|---|---|\n");

            for (Map.Entry<String, List<String>> item : dados.obrasPorEmpreiteira.entrySet()) {
                texto.append("| ").append(item.getKey()).append(" | ")
                        .append(String.join("<br>", item.getValue())).append(" |\n");
            }

            texto.append("\n");
        }

        texto.append("---\n\n## Funcionarios\n\n");
        secao(texto, "Cadastrados", func.cadastrados);
        secao(texto, "Ja existiam no CF Obras", func.jaExistiam);
        secao(texto, "Nao encontrados no TRC", func.semCadastroNoTrc);
        secao(texto, "Sem CPF no TRC", func.semCpf);
        secao(texto, "Empreiteiras sem card no CF Obras", func.semCard);

        if (!dados.arquivoRelatorio.isBlank()) {
            texto.append("---\n\nPlanilha da catraca: `")
                    .append(dados.arquivoRelatorio).append("` (arquivada nesta pasta)\n");
        }

        return texto.toString();
    }

    private void cronometro(StringBuilder texto, Dados dados) {
        Cronometro cronometro = dados.cronometro;

        if (cronometro == null) {
            return;
        }

        long total = cronometro.totalMs();

        texto.append("---\n\n## Cronometro\n\n");
        texto.append("Tempo total **").append(Cronometro.formatar(total)).append("** — ")
                .append(Cronometro.formatar(cronometro.totalNavegadorMs())).append(" (")
                .append(Cronometro.percentual(cronometro.totalNavegadorMs(), total))
                .append(") agindo no navegador e ")
                .append(Cronometro.formatar(cronometro.totalPausaMs())).append(" (")
                .append(Cronometro.percentual(cronometro.totalPausaMs(), total))
                .append(") parado entre uma acao e outra.\n\n");

        List<Cronometro.Estatistica> etapas = cronometro.etapas();

        if (!etapas.isEmpty()) {
            texto.append("### Tempo por etapa\n\n");
            texto.append("| Etapa | Tempo | % | Vezes | Itens | Media/item | Navegador | Parado |\n");
            texto.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");

            for (Cronometro.Estatistica etapa : etapas) {
                texto.append("| ").append(etapa.nome)
                        .append(" | ").append(Cronometro.formatar(etapa.totalMs))
                        .append(" | ").append(Cronometro.percentual(etapa.totalMs, total))
                        .append(" | ").append(etapa.vezes)
                        .append(" | ").append(etapa.itens)
                        .append(" | ").append(etapa.itens == 0 ? "-" : Cronometro.formatar(etapa.mediaItemMs()))
                        .append(" | ").append(Cronometro.formatar(etapa.navegadorMs))
                        .append(" | ").append(Cronometro.formatar(etapa.pausaMs))
                        .append(" |\n");
            }

            texto.append("\n");
        }

        List<Cronometro.Medicao> lentos = cronometro.itensMaisLentos(15);

        if (!lentos.isEmpty()) {
            texto.append("### Os 15 pontos mais demorados\n\n");
            texto.append("| Tempo | Etapa | Item |\n|---:|---|---|\n");

            for (Cronometro.Medicao medicao : lentos) {
                texto.append("| ").append(Cronometro.formatar(medicao.duracaoMs()))
                        .append(" | ").append(medicao.etapa())
                        .append(" | ").append(medicao.detalhe())
                        .append(" |\n");
            }

            texto.append("\n");
        }

        List<Cronometro.UsoNavegador> usos = cronometro.navegador();

        if (!usos.isEmpty()) {
            texto.append("### O que o robo pediu ao navegador\n\n");
            texto.append("| Acao | Chamadas | Tempo | Media | Pior chamada | Sem resultado | Excecoes |\n");
            texto.append("|---|---:|---:|---:|---:|---:|---:|\n");

            for (Cronometro.UsoNavegador uso : usos) {
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

            for (Cronometro.UsoNavegador uso : usos) {
                if (uso.maiorMs >= 5000 && !uso.piorAlvo.isBlank()) {
                    texto.append("Chamada isolada mais lenta: **").append(uso.categoria).append("** de ")
                            .append(Cronometro.formatar(uso.maiorMs)).append(" em `")
                            .append(uso.piorAlvo).append("`\n\n");
                    break;
                }
            }
        }

        List<Cronometro.Pausa> pausas = cronometro.pausas();

        if (!pausas.isEmpty()) {
            texto.append("### Paradas do robo (sleep fixo, banco e processamento)\n\n");
            texto.append("| Faixa | Vezes | Tempo somado |\n|---|---:|---:|\n");

            for (Cronometro.Pausa pausa : pausas) {
                texto.append("| ").append(pausa.faixa)
                        .append(" | ").append(pausa.vezes)
                        .append(" | ").append(Cronometro.formatar(pausa.totalMs))
                        .append(" |\n");
            }

            texto.append("\n");
        }

        List<String> achados = analisador.analisarObra(cronometro);

        if (!achados.isEmpty()) {
            texto.append("### Onde da para ganhar tempo\n\n");

            for (String achado : achados) {
                texto.append("- ").append(achado).append("\n");
            }

            texto.append("\n");
        }
    }

    private void linha(StringBuilder texto, String rotulo, int valor) {
        texto.append("| ").append(rotulo).append(" | ").append(valor).append(" |\n");
    }

    private boolean secao(StringBuilder texto, String titulo, List<String> itens) {
        if (itens == null || itens.isEmpty()) {
            return false;
        }

        texto.append("### ").append(titulo).append(" (").append(itens.size()).append(")\n\n");

        for (String item : itens) {
            texto.append("- ").append(item).append("\n");
        }

        texto.append("\n");
        return true;
    }
}