package com.cury.automacaocatraca.orchestration;

import com.cury.automacaocatraca.cfobras.dto.EmpreiteiraMapeada;
import com.cury.automacaocatraca.cfobras.dto.PlanoConciliacao;
import com.cury.automacaocatraca.cfobras.page.CadastroEmpreiteiraPage;
import com.cury.automacaocatraca.cfobras.page.CfObrasNavigator;
import com.cury.automacaocatraca.cfobras.page.PontoCatracaPage;
import com.cury.automacaocatraca.cfobras.page.VinculoObraPage;
import com.cury.automacaocatraca.cfobras.service.EmpreiteiraLocalizador;
import com.cury.automacaocatraca.cfobras.service.EmpreiteiraMatcher;
import com.cury.automacaocatraca.config.ObraConfig;
import com.cury.automacaocatraca.config.ObrasRegistry;
import com.cury.automacaocatraca.domain.dto.DadosCadastroEmpreiteira;
import com.cury.automacaocatraca.domain.entity.EmpreiteiraCache;
import com.cury.automacaocatraca.domain.entity.EmpreiteiraObraVinculo;
import com.cury.automacaocatraca.domain.entity.ExecucaoLog;
import com.cury.automacaocatraca.domain.entity.Obra;
import com.cury.automacaocatraca.domain.enums.StatusExecucao;
import com.cury.automacaocatraca.domain.mapper.CadastroMapper;
import com.cury.automacaocatraca.domain.service.CorIdentificacaoService;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import com.cury.automacaocatraca.excel.ExcelReportReader;
import com.cury.automacaocatraca.excel.RelatorioFrequencia;
import com.cury.automacaocatraca.repository.EmpreiteiraCacheRepository;
import com.cury.automacaocatraca.repository.EmpreiteiraObraVinculoRepository;
import com.cury.automacaocatraca.repository.ExecucaoLogRepository;
import com.cury.automacaocatraca.repository.ObraRepository;
import com.cury.automacaocatraca.trc.TrcEmployeeExtractor;
import com.cury.automacaocatraca.trc.TrcFuncionarioExtractor;
import com.cury.automacaocatraca.trc.TrcObraSelector;
import com.cury.automacaocatraca.trc.TrcReportExtractor;
import com.cury.automacaocatraca.trc.dto.EmpreiteiraTrc;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class AutomacaoPipeline {

    private static final Logger log = LoggerFactory.getLogger(AutomacaoPipeline.class);
    private static final Set<String> IGNORADAS = Set.of("VISITANTE");

    private final ObrasRegistry obrasRegistry;
    private final SessaoNavegadores sessao;
    private final TrcObraSelector trcObraSelector;
    private final TrcReportExtractor trcReportExtractor;
    private final TrcEmployeeExtractor trcEmployeeExtractor;
    private final TrcFuncionarioExtractor trcFuncionarioExtractor;
    private final ExcelReportReader excelReader;
    private final CfObrasNavigator cfNavigator;
    private final PontoCatracaPage pontoCatracaPage;
    private final CadastroEmpreiteiraPage cadastroPage;
    private final VinculoObraPage vinculoObraPage;
    private final EmpreiteiraMatcher empreiteiraMatcher;
    private final EmpreiteiraLocalizador empreiteiraLocalizador;
    private final ConciliacaoFuncionariosStep funcionariosStep;
    private final CompletarCadastrosStep completarStep;
    private final CadastroMapper cadastroMapper;
    private final CorIdentificacaoService corService;
    private final EmpreiteiraCacheRepository empreiteiraRepository;
    private final ObraRepository obraRepository;
    private final EmpreiteiraObraVinculoRepository vinculoRepository;
    private final ExecucaoLogRepository execucaoLogRepository;
    private final RelatorioExecucaoService relatorioService;
    private final DiagnosticoService diagnosticoService;
    private final CronometroContexto cronometroContexto;

    public AutomacaoPipeline(ObrasRegistry obrasRegistry,
                             SessaoNavegadores sessao,
                             TrcObraSelector trcObraSelector,
                             TrcReportExtractor trcReportExtractor,
                             TrcEmployeeExtractor trcEmployeeExtractor,
                             TrcFuncionarioExtractor trcFuncionarioExtractor,
                             ExcelReportReader excelReader,
                             CfObrasNavigator cfNavigator,
                             PontoCatracaPage pontoCatracaPage,
                             CadastroEmpreiteiraPage cadastroPage,
                             VinculoObraPage vinculoObraPage,
                             EmpreiteiraMatcher empreiteiraMatcher,
                             EmpreiteiraLocalizador empreiteiraLocalizador,
                             ConciliacaoFuncionariosStep funcionariosStep,
                             CompletarCadastrosStep completarStep,
                             CadastroMapper cadastroMapper,
                             CorIdentificacaoService corService,
                             EmpreiteiraCacheRepository empreiteiraRepository,
                             ObraRepository obraRepository,
                             EmpreiteiraObraVinculoRepository vinculoRepository,
                             ExecucaoLogRepository execucaoLogRepository,
                             RelatorioExecucaoService relatorioService,
                             DiagnosticoService diagnosticoService,
                             CronometroContexto cronometroContexto) {
        this.obrasRegistry = obrasRegistry;
        this.sessao = sessao;
        this.trcObraSelector = trcObraSelector;
        this.trcReportExtractor = trcReportExtractor;
        this.trcEmployeeExtractor = trcEmployeeExtractor;
        this.trcFuncionarioExtractor = trcFuncionarioExtractor;
        this.excelReader = excelReader;
        this.cfNavigator = cfNavigator;
        this.pontoCatracaPage = pontoCatracaPage;
        this.cadastroPage = cadastroPage;
        this.vinculoObraPage = vinculoObraPage;
        this.empreiteiraMatcher = empreiteiraMatcher;
        this.empreiteiraLocalizador = empreiteiraLocalizador;
        this.funcionariosStep = funcionariosStep;
        this.completarStep = completarStep;
        this.cadastroMapper = cadastroMapper;
        this.corService = corService;
        this.empreiteiraRepository = empreiteiraRepository;
        this.obraRepository = obraRepository;
        this.vinculoRepository = vinculoRepository;
        this.execucaoLogRepository = execucaoLogRepository;
        this.relatorioService = relatorioService;
        this.diagnosticoService = diagnosticoService;
        this.cronometroContexto = cronometroContexto;
    }

    public ExecucaoLog executar(ObraConfig obra) {
        Path pasta = obrasRegistry.pastaDownload();
        List<String> nomesCfObras = obra.nomesCfObras();
        String nomeObraCfObras = nomesCfObras.get(0);
        ExecucaoLog registro = novoLog(obra);

        WebDriver trc = null;
        WebDriver cf = null;

        sessao.novaObra(obra.codigo());

        int cadastradas = 0;
        int vinculadas = 0;
        ConciliacaoFuncionariosStep.Resumo resumoFuncionarios = new ConciliacaoFuncionariosStep.Resumo();
        StringBuilder problemas = new StringBuilder();

        RelatorioExecucaoService.Dados dados = new RelatorioExecucaoService.Dados();
        dados.codigoObra = obra.codigo();
        dados.nomeObra = String.join(" + ", nomesCfObras);
        dados.dataReferencia = trcReportExtractor.dataDeReferencia();

        Cronometro cronometro = cronometroContexto.iniciar(obra.codigo(), dados.nomeObra);
        cronometro.definirDataReferencia(dados.dataReferencia);
        dados.cronometro = cronometro;

        AtomicBoolean relatorioGerado = new AtomicBoolean(false);
        Thread aoInterromper = engatilharRelatorioDeEmergencia(registro, dados, relatorioGerado);

        try {
            log.info("==========================================================");
            log.info("  EXECUCAO COMPLETA — obra {}", obra.codigo());
            log.info("==========================================================");

            log.info("----- ETAPA 1/8: EXTRACAO NO TRC -----");
            Path arquivo;

            try (Cronometro.Marcacao etapa = cronometro.iniciar(Cronometro.ETAPA_TRC)) {
                try (Cronometro.Marcacao passo = cronometro.iniciar(Cronometro.ETAPA_TRC,
                        "sessao do TRC (abrir ou reaproveitar)")) {
                    trc = sessao.trc();
                }

                try (Cronometro.Marcacao passo = cronometro.iniciar(Cronometro.ETAPA_TRC, "selecionar a obra no TRC")) {
                    trcObraSelector.selecionar(trc, obra);
                }

                try (Cronometro.Marcacao passo = cronometro.iniciar(Cronometro.ETAPA_TRC,
                        "gerar e baixar o relatorio 03.1")) {
                    arquivo = trcReportExtractor.extrairRelatorioDoDia(trc, obra, pasta);
                }
            }

            log.info("Arquivo baixado: {}", arquivo.getFileName());
            dados.arquivoRelatorio = String.valueOf(arquivo.getFileName());
            dados.arquivoOrigem = arquivo;

            log.info("----- ETAPA 2/8: LEITURA DO EXCEL -----");
            RelatorioFrequencia relatorio;

            try (Cronometro.Marcacao etapa = cronometro.iniciar(Cronometro.ETAPA_EXCEL)) {
                relatorio = excelReader.ler(arquivo);
            }

            int totalFuncionarios = relatorio.empreiteiras().stream()
                    .mapToInt(e -> relatorio.funcionariosDa(e).size())
                    .sum();
            log.info("Obra '{}': {} empreiteiras, {} funcionarios distintos",
                    relatorio.nomeObra(), relatorio.empreiteiras().size(), totalFuncionarios);
            dados.empreiteirasNoRelatorio = relatorio.empreiteiras().size();
            dados.funcionariosNoRelatorio = totalFuncionarios;

            if (relatorio.empreiteiras().isEmpty()) {
                log.warn("Nenhum registro no relatorio de {} — obra encerrada sem importar",
                        obra.codigo());
                registro.setStatus(StatusExecucao.SUCESSO);
                dados.pendencias.add("Relatorio da catraca sem registros — nada a importar hoje");
                return registro;
            }

            log.info("----- ETAPA 3/8: CADASTRO DAS EMPREITEIRAS -----");

            try (Cronometro.Marcacao etapa = cronometro.iniciar(Cronometro.ETAPA_EMPREITEIRAS)) {
                try (Cronometro.Marcacao passo = cronometro.iniciar(Cronometro.ETAPA_EMPREITEIRAS,
                        "sessao do CF Obras (abrir ou reaproveitar)")) {
                    cf = sessao.cfObras();
                    cfNavigator.abrirModuloServicos(cf);
                }

                for (String nomeEmpreiteira : relatorio.empreiteiras()) {
                    if (IGNORADAS.contains(NormalizadorNome.normalizar(nomeEmpreiteira))) {
                        continue;
                    }

                    try (Cronometro.Marcacao item = cronometro.iniciar(Cronometro.ETAPA_EMPREITEIRAS,
                            "empreiteira " + nomeEmpreiteira)) {

                        if (cadastrarUma(trc, cf, nomeEmpreiteira, nomeObraCfObras, problemas, relatorio)) {
                            cadastradas++;
                            dados.empreiteirasCadastradas.add(nomeEmpreiteira);
                        }
                    }
                }
            }

            log.info("Empreiteiras cadastradas nesta execucao: {}", cadastradas);

            log.info("----- ETAPA 4/8: VINCULO EMPREITEIRA x OBRA -----");

            try (Cronometro.Marcacao etapa = cronometro.iniciar(Cronometro.ETAPA_VINCULOS)) {
                for (String nomeCf : nomesCfObras) {
                    log.info("--- vinculo em '{}' ---", nomeCf);

                    try (Cronometro.Marcacao item = cronometro.iniciar(Cronometro.ETAPA_VINCULOS,
                            "matriz de " + nomeCf)) {
                        vinculadas += conferirVinculos(cf, trc, relatorio, obra, nomeCf, problemas, dados);
                    }
                }

                try (Cronometro.Marcacao item = cronometro.iniciar(Cronometro.ETAPA_VINCULOS,
                        "espelhar a matriz no banco")) {
                    sincronizarMatriz(cf, trc, dados);
                }
            }

            log.info("Vinculos criados nesta execucao: {}", vinculadas);

            log.info("----- ETAPA 5/8: CONCILIACAO E CADASTRO DE FUNCIONARIOS -----");

            try (Cronometro.Marcacao etapa = cronometro.iniciar(Cronometro.ETAPA_FUNCIONARIOS)) {
                PlanoConciliacao plano = empreiteiraMatcher.conciliar(relatorio);
                resumoFuncionarios = funcionariosStep.executar(trc, cf, plano, cronometro);
            }

            dados.funcionarios = resumoFuncionarios;
            problemas.append(resumoFuncionarios.problemasTexto());

            List<EmpreiteiraMapeada> aindaFaltando = new ArrayList<>();

            try (Cronometro.Marcacao etapa = cronometro.iniciar(Cronometro.ETAPA_IMPORTACAO)) {
                for (String nomeCf : nomesCfObras) {
                    log.info("----- ETAPAS 6-7/8: '{}' -----", nomeCf);
                    aindaFaltando.addAll(subirEImportar(cf, arquivo, nomeCf, problemas, dados, cronometro));
                }
            }

            log.info("----- ETAPA 8/8: COMPLETAR CADASTROS INCOMPLETOS -----");
            CompletarCadastrosStep.Resumo completados;

            try (Cronometro.Marcacao etapa = cronometro.iniciar(Cronometro.ETAPA_COMPLETAR)) {
                completados = completarStep.executar(trc, cf);
            }

            for (String item : completados.semDadosNoTrc) {
                dados.pendencias.add("cadastro incompleto e sem dados no TRC: " + item);
            }
            for (String item : completados.falhas) {
                dados.pendencias.add("falha ao completar cadastro: " + item);
            }

            boolean tudoLimpo = aindaFaltando.isEmpty()
                    && resumoFuncionarios.naoCadastrados.isEmpty();

            registro.setStatus(tudoLimpo ? StatusExecucao.SUCESSO : StatusExecucao.PARCIAL);
            registro.setEmpreiteirasCadastradas(cadastradas);
            registro.setFuncionariosCadastrados(resumoFuncionarios.cadastrados.size());
            registro.setFuncoesDesconhecidas(juntar(resumoFuncionarios.funcoesDesconhecidas, 2000));
            registro.setAtribuicoesGestor(juntar(resumoFuncionarios.atribuicoesGestor, 2000));
            registro.setMensagemErro(cortar(problemas.toString(), 3900));

            log.info("==========================================================");
            log.info("  CONCLUIDO — status {}", registro.getStatus());
            log.info("  {}", resumoFuncionarios.resumoTexto());
            log.info("==========================================================");

        } catch (Exception e) {
            log.error("EXECUCAO FALHOU: {}", e.getMessage(), e);
            diagnosticoService.capturar(cf, obra.codigo(), "cfobras-" + e.getClass().getSimpleName());
            diagnosticoService.capturar(trc, obra.codigo(), "trc-" + e.getClass().getSimpleName());
            dados.pendencias.add("Falha: " + e.getMessage() + " — print e HTML na subpasta falhas/");
            sessao.descartarTudo();
            registro.setStatus(StatusExecucao.FALHA);
            registro.setEmpreiteirasCadastradas(cadastradas);
            registro.setFuncionariosCadastrados(resumoFuncionarios.cadastrados.size());
            registro.setFuncoesDesconhecidas(juntar(resumoFuncionarios.funcoesDesconhecidas, 2000));
            registro.setAtribuicoesGestor(juntar(resumoFuncionarios.atribuicoesGestor, 2000));
            problemas.append("ERRO FATAL: ").append(e.getMessage());
            registro.setMensagemErro(cortar(problemas.toString(), 3900));
        } finally {
            try (Cronometro.Marcacao etapa = cronometro.iniciar(Cronometro.ETAPA_ENCERRAMENTO)) {
                execucaoLogRepository.save(registro);
            }

            dados.fim = LocalDateTime.now();
            dados.funcionarios = resumoFuncionarios;

            cronometro.encerrar();

            log.info("Cronometro da obra {}: {} no total, {} no navegador, {} parado",
                    obra.codigo(),
                    Cronometro.formatar(cronometro.totalMs()),
                    Cronometro.formatar(cronometro.totalNavegadorMs()),
                    Cronometro.formatar(cronometro.totalPausaMs()));

            if (relatorioGerado.compareAndSet(false, true)) {
                relatorioService.gerar(registro, dados);
            }

            cronometroContexto.encerrar(registro, dados);
            desengatilhar(aoInterromper);
        }

        return registro;
    }

    private Thread engatilharRelatorioDeEmergencia(ExecucaoLog registro,
                                                   RelatorioExecucaoService.Dados dados,
                                                   AtomicBoolean jaGerado) {
        Thread gancho = new Thread(() -> {
            if (!jaGerado.compareAndSet(false, true)) {
                return;
            }

            dados.fim = LocalDateTime.now();

            if (dados.cronometro != null) {
                dados.cronometro.encerrar();
            }

            dados.pendencias.add("EXECUCAO INTERROMPIDA — o relatorio cobre so o que rodou ate aqui");
            relatorioService.gerar(registro, dados);
        }, "relatorio-de-emergencia");

        try {
            Runtime.getRuntime().addShutdownHook(gancho);
        } catch (IllegalStateException e) {
            log.warn("JVM em desligamento — sem relatorio de emergencia para esta obra");
            return null;
        }

        return gancho;
    }

    private void desengatilhar(Thread gancho) {
        if (gancho == null) {
            return;
        }

        try {
            Runtime.getRuntime().removeShutdownHook(gancho);
        } catch (IllegalStateException ignorado) {
        }
    }

    private List<EmpreiteiraMapeada> subirEImportar(WebDriver cf, Path arquivo,
                                                    String nomeObraCfObras,
                                                    StringBuilder problemas,
                                                    RelatorioExecucaoService.Dados dados,
                                                    Cronometro cronometro) {

        try (Cronometro.Marcacao passo = cronometro.iniciar(Cronometro.ETAPA_IMPORTACAO,
                "subir a planilha em " + nomeObraCfObras)) {
            pontoCatracaPage.abrir(cf);
            pontoCatracaPage.selecionarObra(cf, nomeObraCfObras);
            pontoCatracaPage.enviarArquivo(cf, arquivo);
        }

        try (Cronometro.Marcacao passo = cronometro.iniciar(Cronometro.ETAPA_IMPORTACAO,
                "mapear empreiteiras em " + nomeObraCfObras)) {
            pontoCatracaPage.mapearPorNome(cf);
            mapearPeloBanco(cf);
        }

        List<EmpreiteiraMapeada> mapeamentoFinal = pontoCatracaPage.lerMapeamento(cf);
        List<EmpreiteiraMapeada> faltando = filtrarPendentes(mapeamentoFinal);

        for (EmpreiteiraMapeada item : faltando) {
            log.warn("[SEM VINCULO] {} sera ignorada na importacao de '{}'",
                    item.nomeArquivo(), nomeObraCfObras);
            problemas.append("sem vinculo em ").append(nomeObraCfObras)
                    .append(": ").append(item.nomeArquivo()).append("; ");
            dados.pendencias.add("sem vinculo na importacao de " + nomeObraCfObras
                    + ": " + item.nomeArquivo());
        }

        log.info("Mapeadas em '{}': {} de {}", nomeObraCfObras,
                mapeamentoFinal.stream().filter(EmpreiteiraMapeada::mapeada).count(),
                mapeamentoFinal.size());

        boolean confirmou;

        try (Cronometro.Marcacao passo = cronometro.iniciar(Cronometro.ETAPA_IMPORTACAO,
                "importacao no CF Obras em " + nomeObraCfObras)) {
            confirmou = pontoCatracaPage.importar(cf);
        }

        if (!confirmou) {
            problemas.append("importacao sem confirmacao em ").append(nomeObraCfObras).append("; ");
            dados.pendencias.add("A importacao em " + nomeObraCfObras + " foi acionada mas o "
                    + "CF Obras nao confirmou na tela — conferir manualmente");
        }

        log.info("Resumo do CF Obras ({}): {}", nomeObraCfObras, pontoCatracaPage.resumo(cf));

        return faltando;
    }

    private int conferirVinculos(WebDriver cf, WebDriver trc, RelatorioFrequencia relatorio,
                                 ObraConfig obraConfig, String nomeObraCfObras,
                                 StringBuilder problemas, RelatorioExecucaoService.Dados dados) {
        vinculoObraPage.abrir(cf);

        if (!vinculoObraPage.painelDisponivel(cf)) {
            log.warn("Matriz de vinculos indisponivel — etapa pulada");
            problemas.append("matriz de vinculos indisponivel; ");
            dados.pendencias.add("matriz de vinculos indisponivel — etapa pulada");
            return 0;
        }

        garantirObra(obraConfig, nomeObraCfObras, null);
        int criados = 0;

        for (String nomeEmpreiteira : relatorio.empreiteiras()) {
            String normalizado = NormalizadorNome.normalizar(nomeEmpreiteira);

            if (IGNORADAS.contains(normalizado)) {
                continue;
            }

            VinculoObraPage.Resultado resultado =
                    vinculoObraPage.garantirVinculo(cf, nomeEmpreiteira, nomeObraCfObras);

            switch (resultado) {
                case VINCULADA_AGORA -> {
                    criados++;
                    dados.vinculosCriados.add(nomeEmpreiteira);
                }
                case NAO_ENCONTRADA -> {
                    problemas.append("sem card no CF Obras: ").append(nomeEmpreiteira).append("; ");
                    dados.pendencias.add("sem card no CF Obras: " + nomeEmpreiteira);
                }
                case JA_VINCULADA, VISIVEL_EM_TODAS -> {

                }
                default -> {
                    problemas.append("falha no vinculo: ").append(nomeEmpreiteira).append("; ");
                    dados.pendencias.add("falha ao vincular: " + nomeEmpreiteira);
                }
            }
        }

        return criados;
    }

    private void sincronizarMatriz(WebDriver cf, WebDriver trc, RelatorioExecucaoService.Dados dados) {
        vinculoObraPage.abrir(cf);

        List<VinculoObraPage.LinhaMatriz> matriz = vinculoObraPage.lerMatrizCompleta(cf);

        if (matriz.isEmpty()) {
            log.warn("Nao consegui ler a matriz completa — vinculos do banco ficam como estavam");
            return;
        }

        Map<String, Obra> obrasPorNome = new HashMap<>();

        for (Obra obra : obraRepository.findAll()) {
            obrasPorNome.put(obra.getNomeNormalizado(), obra);
        }

        Map<Long, List<EmpreiteiraObraVinculo>> vinculosPorEmpreiteira = new HashMap<>();

        for (EmpreiteiraObraVinculo vinculo : vinculoRepository.findAll()) {
            if (vinculo.getEmpreiteira() == null || vinculo.getEmpreiteira().getId() == null) {
                continue;
            }

            vinculosPorEmpreiteira
                    .computeIfAbsent(vinculo.getEmpreiteira().getId(), id -> new ArrayList<>())
                    .add(vinculo);
        }

        List<EmpreiteiraObraVinculo> novos = new ArrayList<>();
        List<EmpreiteiraObraVinculo> obsoletos = new ArrayList<>();

        for (VinculoObraPage.LinhaMatriz linha : matriz) {
            String nome = vazio(linha.fantasia()) ? linha.razaoSocial() : linha.fantasia();

            if (vazio(nome)) {
                continue;
            }

            EmpreiteiraCache empreiteira = garantirEmpreiteira(
                    nome, linha.idCfObras(), linha.fantasia(), linha.razaoSocial(), dados);

            completarCnpj(trc, empreiteira, linha, dados);

            List<EmpreiteiraObraVinculo> jaGravados =
                    vinculosPorEmpreiteira.getOrDefault(empreiteira.getId(), new ArrayList<>());

            List<String> nomesDasObras = new ArrayList<>();
            List<String> normalizadosDaMatriz = new ArrayList<>();

            for (String nomeObra : linha.obras()) {
                Obra obra = obraDoCache(nomeObra, obrasPorNome);

                nomesDasObras.add(obra.getNome());
                normalizadosDaMatriz.add(obra.getNomeNormalizado());

                boolean jaExiste = jaGravados.stream()
                        .anyMatch(v -> v.getObra() != null && obra.getId().equals(v.getObra().getId()));

                if (!jaExiste) {
                    EmpreiteiraObraVinculo vinculo = new EmpreiteiraObraVinculo();
                    vinculo.setEmpreiteira(empreiteira);
                    vinculo.setObra(obra);
                    vinculo.setDataVerificacao(LocalDateTime.now());
                    novos.add(vinculo);
                }
            }

            for (EmpreiteiraObraVinculo existente : jaGravados) {
                if (existente.getObra() == null
                        || !normalizadosDaMatriz.contains(existente.getObra().getNomeNormalizado())) {
                    obsoletos.add(existente);
                }
            }

            if (!nomesDasObras.isEmpty()) {
                dados.obrasPorEmpreiteira.put(nome, nomesDasObras);
                log.info("  {} -> {}", nome, String.join(", ", nomesDasObras));
            }
        }

        if (!novos.isEmpty()) {
            vinculoRepository.saveAll(novos);
        }

        if (!obsoletos.isEmpty()) {
            vinculoRepository.deleteAll(obsoletos);
        }

        log.info("Vinculos espelhados no banco: {} novos, {} removidos ({} empreiteiros na matriz)",
                novos.size(), obsoletos.size(), matriz.size());
    }

    private Obra obraDoCache(String nomeObra, Map<String, Obra> cache) {
        String normalizado = NormalizadorNome.normalizar(nomeObra);
        Obra existente = cache.get(normalizado);

        if (existente != null) {
            return existente;
        }

        log.info("Obra '{}' existe no CF Obras mas nao esta no banco — gravada agora", nomeObra);

        Obra obra = new Obra();
        obra.setNome(nomeObra);
        obra.setNomeNormalizado(normalizado);
        obra.setDataVerificacao(LocalDateTime.now());

        Obra salva = obraRepository.save(obra);
        cache.put(normalizado, salva);

        return salva;
    }

    private void completarCnpj(WebDriver trc, EmpreiteiraCache empreiteira,
                               VinculoObraPage.LinhaMatriz linha,
                               RelatorioExecucaoService.Dados dados) {
        if (!vazio(empreiteira.getCnpj())) {
            return;
        }

        Optional<String> cnpj = trcEmployeeExtractor.cnpjDe(trc, linha.razaoSocial());

        if (cnpj.isEmpty() && !vazio(linha.fantasia())) {
            cnpj = trcEmployeeExtractor.cnpjDe(trc, linha.fantasia());
        }

        if (cnpj.isEmpty()) {
            log.warn("Sem CNPJ para '{}' — nao achei no cadastro de empreiteiras do TRC",
                    empreiteira.getNomeTrc());
            dados.pendencias.add("sem CNPJ no TRC: " + empreiteira.getNomeTrc());
            return;
        }

        Optional<EmpreiteiraCache> dono = empreiteiraRepository.findByCnpj(somenteDigitos(cnpj.get()));

        if (dono.isPresent() && !dono.get().getId().equals(empreiteira.getId())) {

            log.warn("CNPJ {} ja esta na linha '{}' — '{}' parece ser duplicata, conferir no banco",
                    cnpj.get(), dono.get().getNomeTrc(), empreiteira.getNomeTrc());
            dados.pendencias.add("possivel empreiteira duplicada no banco: '"
                    + empreiteira.getNomeTrc() + "' e '" + dono.get().getNomeTrc()
                    + "' com o mesmo CNPJ " + cnpj.get());
            return;
        }

        empreiteira.setCnpj(somenteDigitos(cnpj.get()));
        empreiteira.setDataUltimaVerificacao(LocalDateTime.now());

        try {
            empreiteiraRepository.save(empreiteira);
            log.info("CNPJ completado para '{}': {}", empreiteira.getNomeTrc(), cnpj.get());
        } catch (Exception e) {
            log.warn("Nao consegui gravar o CNPJ de '{}': {}",
                    empreiteira.getNomeTrc(), e.getMessage());
        }
    }

    private Obra garantirObra(ObraConfig obraConfig, String nomeObraCfObras, String idCfObras) {
        String normalizado = NormalizadorNome.normalizar(nomeObraCfObras);

        Obra obra = obraRepository.findByNomeNormalizado(normalizado).orElseGet(Obra::new);

        obra.setCodigo(obraConfig.codigo());
        obra.setNome(nomeObraCfObras);
        obra.setNomeNormalizado(normalizado);
        obra.setDataVerificacao(LocalDateTime.now());

        if (idCfObras != null && !idCfObras.isBlank()) {
            obra.setIdCfObras(idCfObras);
        }

        return obraRepository.save(obra);
    }

    private EmpreiteiraCache garantirEmpreiteira(String nomeEmpreiteira, String idCfObras,
                                                 String fantasia, String razao,
                                                 RelatorioExecucaoService.Dados dados) {
        List<String> nomes = new ArrayList<>();
        nomes.add(razao);
        nomes.add(fantasia);
        nomes.add(nomeEmpreiteira);

        EmpreiteiraCache empreiteira = empreiteiraLocalizador
                .localizar(idCfObras, null, nomes, dados.pendencias)
                .orElse(null);

        if (empreiteira != null && !vazio(idCfObras) && !vazio(empreiteira.getIdCfObras())
                && !idCfObras.equals(empreiteira.getIdCfObras())) {

            log.warn("'{}' casou pelo nome com a linha '{}', mas o id do CF Obras e outro — gravando linha nova",
                    nomeEmpreiteira, empreiteira.getNomeTrc());
            empreiteira = null;
        }

        boolean mudou = false;

        if (empreiteira == null) {
            empreiteira = new EmpreiteiraCache();
            mudou = true;
        }

        if (vazio(empreiteira.getNomeTrc())) {
            empreiteira.setNomeTrc(vazio(razao) ? nomeEmpreiteira : razao);
            mudou = true;
        }

        if (vazio(empreiteira.getNomeNormalizado())) {
            empreiteira.setNomeNormalizado(nomeNormalizadoLivre(empreiteira.getNomeTrc(), idCfObras));
            mudou = true;
        }

        if (!empreiteira.isCadastradaNoCfObras()) {
            empreiteira.setCadastradaNoCfObras(true);
            mudou = true;
        }

        if (!vazio(idCfObras) && !idCfObras.equals(empreiteira.getIdCfObras())) {
            empreiteira.setIdCfObras(idCfObras);
            mudou = true;
        }

        if (vazio(empreiteira.getNomeFantasia()) && !vazio(fantasia)) {
            empreiteira.setNomeFantasia(fantasia);
            mudou = true;
        }

        if (vazio(empreiteira.getRazaoSocial()) && !vazio(razao)) {
            empreiteira.setRazaoSocial(razao);
            mudou = true;
        }

        if (!mudou) {
            return empreiteira;
        }

        empreiteira.setDataUltimaVerificacao(LocalDateTime.now());

        return empreiteiraRepository.save(empreiteira);
    }

    private String nomeNormalizadoLivre(String nome, String idCfObras) {
        String base = NormalizadorNome.normalizar(nome);

        if (empreiteiraRepository.findByNomeNormalizado(base).isEmpty()) {
            return base;
        }

        String comId = vazio(idCfObras) ? base + " 2" : base + " " + idCfObras;

        log.warn("Nome normalizado '{}' ja esta em uso por outra empreiteira — gravando como '{}'",
                base, comId);

        return comId;
    }

    private void mapearPeloBanco(WebDriver cf) {
        List<EmpreiteiraMapeada> atual = pontoCatracaPage.lerMapeamento(cf);
        int mapeadas = 0;

        for (EmpreiteiraMapeada item : atual) {
            if (item.mapeada()) {
                continue;
            }

            String normalizado = NormalizadorNome.normalizar(item.nomeArquivo());

            if (IGNORADAS.contains(normalizado)) {
                continue;
            }

            Optional<EmpreiteiraCache> registro = acharEmpreiteira(item.nomeArquivo());
            String id = registro.map(EmpreiteiraCache::getIdCfObras).orElse(null);

            if (id == null || id.isBlank()) {
                log.warn("'{}' sem id do CF Obras no banco — nao da para mapear por id",
                        item.nomeArquivo());
                continue;
            }

            if (pontoCatracaPage.definirMapeamento(cf, item.nomeNormalizado(), id)) {
                mapeadas++;
            }
        }

        log.info("Mapeamento pelo id guardado no banco: {} empreiteiras", mapeadas);
    }

    private Optional<EmpreiteiraCache> acharEmpreiteira(String nome) {
        return empreiteiraLocalizador.porNome(nome);
    }

    private String somenteDigitos(String texto) {
        return texto == null ? null : texto.replaceAll("\\D", "");
    }

    private boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    private boolean cadastrarUma(WebDriver trc, WebDriver cf, String nome,
                                 String nomeObraCfObras, StringBuilder problemas,
                                 RelatorioFrequencia relatorio) {
        String normalizado = NormalizadorNome.normalizar(nome);
        Optional<EmpreiteiraCache> emCache = empreiteiraRepository.findByNomeNormalizado(normalizado);

        cadastroPage.abrir(cf);

        String cnpjConhecido = emCache.map(EmpreiteiraCache::getCnpj).orElse(null);

        if (cadastroPage.jaCadastrada(cf, nome, cnpjConhecido)) {
            log.info("[JA EXISTE] {} — nao cadastro de novo", nome);
            marcarComoCadastrada(normalizado, nome);
            return false;
        }

        Optional<EmpreiteiraTrc> origemOpt = obterDados(trc, nome, emCache);

        if (origemOpt.isEmpty()) {
            Optional<String> pelaEquipe = nomeNoTrcPeloFuncionario(trc, relatorio, nome);

            if (pelaEquipe.isPresent()) {
                log.info("'{}' identificada no TRC como '{}' pelo cadastro do funcionario",
                        nome, pelaEquipe.get());

                origemOpt = trcEmployeeExtractor.extrairEmpreiteira(trc, pelaEquipe.get());
            }
        }

        if (origemOpt.isEmpty()) {
            log.warn("[PULANDO] {} — nao encontrada no TRC", nome);
            problemas.append("nao achou no TRC: ").append(nome).append("; ");
            return false;
        }

        EmpreiteiraTrc origem = origemOpt.get();

        if (origem.cnpj() == null || origem.cnpj().isBlank()) {
            log.warn("[PULANDO] {} — sem CNPJ", nome);
            problemas.append("sem cnpj: ").append(nome).append("; ");
            return false;
        }

        String cor = escolherCor(emCache);
        DadosCadastroEmpreiteira destino = cadastroMapper.mapear(origem, cor);

        log.info("[CADASTRANDO] {} | CNPJ {} | cor {}", nome, destino.cnpj(), cor);

        try {
            String resultado = cadastroPage.cadastrar(cf, destino, nomeObraCfObras);
            boolean sucesso = resultadoIndicaSucesso(resultado);

            salvarNoBanco(origem, normalizado, nome, cor, sucesso);

            if (sucesso) {
                log.info("[OK] {} cadastrada", nome);
                return true;
            }

            log.warn("[FALHOU] {} -> {}", nome, resultado);
            problemas.append("falha no cadastro: ").append(nome).append("; ");
            return false;

        } catch (Exception e) {
            log.error("[ERRO] {} -> {}", nome, e.getMessage());
            problemas.append("erro: ").append(nome).append("; ");
            return false;
        }
    }

    private Optional<String> nomeNoTrcPeloFuncionario(WebDriver trc,
                                                      RelatorioFrequencia relatorio,
                                                      String empreiteiraNoRelatorio) {
        for (String funcionario : relatorio.funcionariosDa(empreiteiraNoRelatorio)) {
            Optional<TrcFuncionarioExtractor.LinhaTrc> linha =
                    trcFuncionarioExtractor.localizarNaListagem(trc, funcionario);

            if (linha.isEmpty() || vazio(linha.get().empreiteira())) {
                continue;
            }

            String noTrc = linha.get().empreiteira();

            log.info("Funcionario '{}' esta no TRC sob a empreiteira '{}'", funcionario, noTrc);

            return Optional.of(noTrc);
        }

        return Optional.empty();
    }

    private List<EmpreiteiraMapeada> filtrarPendentes(List<EmpreiteiraMapeada> mapeamento) {
        List<EmpreiteiraMapeada> pendentes = new ArrayList<>();

        for (EmpreiteiraMapeada item : mapeamento) {
            if (item.mapeada()) {
                continue;
            }
            if (IGNORADAS.contains(NormalizadorNome.normalizar(item.nomeArquivo()))) {
                continue;
            }
            pendentes.add(item);
        }

        return pendentes;
    }

    private long contarIgnoradas(List<EmpreiteiraMapeada> mapeamento) {
        return mapeamento.stream()
                .filter(i -> IGNORADAS.contains(NormalizadorNome.normalizar(i.nomeArquivo())))
                .count();
    }

    private Optional<EmpreiteiraTrc> obterDados(WebDriver trc, String nome,
                                                Optional<EmpreiteiraCache> emCache) {
        if (emCache.isPresent()
                && emCache.get().getCnpj() != null
                && !emCache.get().getCnpj().isBlank()) {

            EmpreiteiraCache c = emCache.get();
            log.info("[BANCO] usando dados salvos de '{}'", nome);
            return Optional.of(new EmpreiteiraTrc(
                    c.getRazaoSocial(), c.getCnpj(), c.getNomeFantasia(), "", "", "", ""));
        }

        return trcEmployeeExtractor.extrairEmpreiteira(trc, nome);
    }

    private String escolherCor(Optional<EmpreiteiraCache> emCache) {
        return emCache.map(EmpreiteiraCache::getCorIdentificacao)
                .filter(c -> c != null && !c.isBlank())
                .orElseGet(corService::sortearCorDisponivel);
    }

    private boolean resultadoIndicaSucesso(String resultado) {
        String texto = resultado == null ? "" : resultado.toLowerCase();

        if (texto.contains("erro") || texto.contains("obrigat")
                || texto.contains("falha") || texto.contains("invalid")
                || texto.contains("preencha")) {
            return false;
        }

        return texto.contains("sucesso") || texto.contains("cadastrad")
                || texto.contains("salvo") || texto.contains("formulario limpo");
    }

    private void marcarComoCadastrada(String normalizado, String nome) {
        EmpreiteiraCache registro = empreiteiraLocalizador.porNome(nome)
                .orElseGet(EmpreiteiraCache::new);

        if (registro.getId() != null) {
            log.info("'{}' ja tem linha no banco (id {}, nome '{}') — atualizando em vez de criar outra",
                    nome, registro.getId(), registro.getNomeTrc());
        }

        if (vazio(registro.getNomeTrc())) {
            registro.setNomeTrc(nome);
        }

        if (vazio(registro.getNomeNormalizado())) {
            registro.setNomeNormalizado(normalizado);
        }

        registro.setCadastradaNoCfObras(true);
        registro.setDataUltimaVerificacao(LocalDateTime.now());

        empreiteiraRepository.save(registro);
    }

    private void salvarNoBanco(EmpreiteiraTrc origem, String normalizado,
                               String nomeArquivo, String cor, boolean cadastrada) {
        String cnpj = somenteDigitos(origem.cnpj());

        EmpreiteiraCache registro = empreiteiraLocalizador.porCnpj(cnpj)
                .or(() -> empreiteiraLocalizador.porNome(nomeArquivo))
                .orElseGet(EmpreiteiraCache::new);

        if (registro.getId() != null) {
            log.info("'{}' ja tem linha no banco (id {}) — atualizando em vez de criar outra",
                    nomeArquivo, registro.getId());
        }

        if (vazio(registro.getNomeTrc())) {
            registro.setNomeTrc(nomeArquivo);
        }

        if (vazio(registro.getNomeNormalizado())) {
            registro.setNomeNormalizado(normalizado);
        }

        registro.setCnpj(cnpj);
        registro.setRazaoSocial(origem.razaoSocial());
        registro.setNomeFantasia(origem.nomeFantasia());
        registro.setCorIdentificacao(cor);
        registro.setCadastradaNoCfObras(cadastrada);
        registro.setDataUltimaVerificacao(LocalDateTime.now());

        empreiteiraRepository.save(registro);
    }

    private ExecucaoLog novoLog(ObraConfig obra) {
        ExecucaoLog registro = new ExecucaoLog();
        registro.setCodigoObra(obra.codigo());
        registro.setDataExecucao(LocalDateTime.now());
        registro.setStatus(StatusExecucao.FALHA);
        return registro;
    }

    private String juntar(List<String> itens, int limite) {
        if (itens == null || itens.isEmpty()) {
            return null;
        }
        return cortar(String.join("; ", itens), limite);
    }

    private String cortar(String texto, int limite) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.length() > limite ? texto.substring(0, limite) : texto;
    }
}