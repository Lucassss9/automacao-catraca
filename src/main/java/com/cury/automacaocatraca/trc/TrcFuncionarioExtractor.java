package com.cury.automacaocatraca.trc;

import com.cury.automacaocatraca.config.TrcProperties;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import com.cury.automacaocatraca.trc.dto.FuncionarioTrc;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class TrcFuncionarioExtractor {

    private static final Logger log = LoggerFactory.getLogger(TrcFuncionarioExtractor.class);

    private static final By TABELA = By.id("table1");
    private static final By LINHAS = By.cssSelector("#table1 tbody tr");
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String CAMINHO_LISTA = "/home/funcionarios";
    private static final String TOKEN_EDICAO = "alterFuncionario";
    private static final int TENTATIVAS = 4;

    private static final List<String> IDS_RG =
            List.of("rg_funcionario", "rg", "num_rg");
    private static final List<String> IDS_NASCIMENTO =
            List.of("dt_nascimento", "data_nascimento", "nascimento");
    private static final List<String> IDS_TELEFONE =
            List.of("f_telefone_funcionario", "telefone_funcionario", "telefone", "celular");
    private static final List<String> IDS_EMAIL =
            List.of("f_email_funcionario", "email_funcionario", "email", "e_mail");
    private static final List<String> IDS_ENTRADA =
            List.of("dt_entrada", "entrada_obra", "data_entrada");
    private static final List<String> IDS_NOME =
            List.of("nome_funcionario", "nome", "txtNome");
    private static final List<String> IDS_CPF =
            List.of("cpf_funcionario", "cpf", "num_cpf");
    private static final List<String> IDS_FUNCAO =
            List.of("codigo_funcao", "funcao", "id_funcao", "cargo");

    private static final String ID_CHECK_DESLIGADO = "check_desligado";
    private static final String ID_CHECK_ATIVO = "check_ativo";

    private static final String NORMALIZADORES_JS =
            "function norm(t){ return (t||'').replace(/\\u00a0/g,' ').normalize('NFD')"
                    + "  .replace(/[\\u0300-\\u036f]/g,'')"
                    + "  .toUpperCase().replace(/\\s+/g,' ').trim(); }"
                    + "function compact(t){ return norm(t).replace(/[^A-Z0-9]/g,''); }";

    public record LinhaTrc(String id, String nome, String funcao, String empreiteira, String cpf) {}

    private final Map<String, LinhaTrc> listagem = new LinkedHashMap<>();
    private boolean ativosCarregados;
    private boolean inativosCarregados;

    private final TrcProperties properties;
    private final TrcAuthenticator authenticator;

    public TrcFuncionarioExtractor(TrcProperties properties, TrcAuthenticator authenticator) {
        this.properties = properties;
        this.authenticator = authenticator;
    }

    public void limparCache() {
        listagem.clear();
        ativosCarregados = false;
        inativosCarregados = false;
    }

    public Optional<FuncionarioTrc> extrairFuncionario(WebDriver driver, String nomeBusca,
                                                       String empreiteiraNome) {
        try {
            garantirListagem(driver, false);
            LinhaTrc linha = procurar(nomeBusca);

            if (linha == null) {
                log.info("'{}' nao esta entre os ativos — recarregando incluindo inativos", nomeBusca);
                garantirListagem(driver, true);
                linha = procurar(nomeBusca);
            }

            if (linha == null) {
                log.warn("Funcionario '{}' nao encontrado no TRC ({} linhas na listagem)",
                        nomeBusca, listagem.size());
                return Optional.empty();
            }

            conferirEmpreiteira(nomeBusca, empreiteiraNome, linha.empreiteira());

            return Optional.of(montar(driver, linha, empreiteiraNome));

        } catch (Exception e) {
            log.error("Erro ao buscar funcionario '{}' no TRC: {}", nomeBusca, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<LinhaTrc> localizarNaListagem(WebDriver driver, String nomeBusca) {
        try {
            garantirListagem(driver, false);
            LinhaTrc linha = procurar(nomeBusca);

            if (linha == null) {
                garantirListagem(driver, true);
                linha = procurar(nomeBusca);
            }

            return Optional.ofNullable(linha);

        } catch (Exception e) {
            log.warn("Erro ao localizar '{}' na listagem do TRC: {}", nomeBusca, e.getMessage());
            return Optional.empty();
        }
    }

    private FuncionarioTrc montar(WebDriver driver, LinhaTrc linha, String empreiteiraNome) {
        Map<String, String> extra = lerComplemento(driver, linha.id(), linha.nome());

        String cpf = linha.cpf();
        String funcao = linha.funcao();

        if (cpf.isBlank()) {
            cpf = extra.getOrDefault("cpf", "");
        }
        if (funcao.isBlank()) {
            funcao = extra.getOrDefault("funcao", "");
        }

        if (cpf.isBlank() || funcao.isBlank()) {
            log.warn("Leitura incompleta de '{}': cpf='{}' funcao='{}'", linha.nome(), cpf, funcao);
        }

        return new FuncionarioTrc(
                linha.nome(),
                cpf,
                extra.getOrDefault("rg", ""),
                data(extra.get("nascimento")),
                funcao,
                extra.getOrDefault("telefone", ""),
                extra.getOrDefault("email", ""),
                empreiteiraNome,
                data(extra.get("entrada")));
    }

    private LinhaTrc procurar(String nomeBusca) {
        String alvo = NormalizadorNome.normalizar(nomeBusca);
        LinhaTrc exato = listagem.get(alvo);

        if (exato != null) {
            log.info("'{}' encontrado na listagem por match exato (id {})", nomeBusca, exato.id());
            return exato;
        }

        String alvoC = alvo.replaceAll("[^A-Z0-9]", "");
        LinhaTrc parcial = null;

        for (Map.Entry<String, LinhaTrc> item : listagem.entrySet()) {
            String chaveC = item.getKey().replaceAll("[^A-Z0-9]", "");

            if (chaveC.equals(alvoC)) {
                log.info("'{}' encontrado por match compacto (id {})", nomeBusca, item.getValue().id());
                return item.getValue();
            }
            if (parcial == null && alvoC.length() > 8 && chaveC.length() > 8
                    && (chaveC.contains(alvoC) || alvoC.contains(chaveC))) {
                parcial = item.getValue();
            }
        }

        if (parcial != null) {
            log.warn("'{}' casado por match PARCIAL (id {}) — conferir", nomeBusca, parcial.id());
        }

        return parcial;
    }

    private void garantirListagem(WebDriver driver, boolean incluirInativos) {
        if (incluirInativos ? inativosCarregados : ativosCarregados) {
            return;
        }

        for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
            try {
                abrirListagem(driver, tentativa);

                if (incluirInativos) {
                    marcarInativos(driver);
                }

                if (!aguardarTabela(driver)) {
                    log.warn("Listagem do TRC nao carregou (tentativa {}/{})", tentativa, TENTATIVAS);
                    continue;
                }

                int lidas = capturarLinhas(driver);

                if (lidas == 0) {
                    log.warn("Listagem do TRC veio vazia (tentativa {}/{})", tentativa, TENTATIVAS);
                    continue;
                }

                if (incluirInativos) {
                    inativosCarregados = true;
                } else {
                    ativosCarregados = true;
                }

                log.info("Listagem de funcionarios do TRC carregada: {} linhas em cache{}",
                        listagem.size(), incluirInativos ? " (com inativos)" : "");
                return;

            } catch (Exception e) {
                log.warn("Falha ao carregar a listagem (tentativa {}/{}): {}",
                        tentativa, TENTATIVAS, e.getMessage());
            }
        }

        log.error("Nao consegui carregar a listagem de funcionarios do TRC apos {} tentativas",
                TENTATIVAS);
    }

    private void abrirListagem(WebDriver driver, int tentativa) {
        switch (tentativa) {
            case 1 -> driver.get(baseUrl() + CAMINHO_LISTA);
            case 2 -> {
                log.info("Recarregando a listagem do TRC");
                driver.navigate().refresh();
            }
            case 3 -> {
                log.info("Abrindo a listagem do TRC em uma aba nova");
                abrirEmAbaNova(driver);
            }
            default -> {
                log.info("Refazendo login no TRC antes de tentar de novo");
                authenticator.login(driver);
                driver.get(baseUrl() + CAMINHO_LISTA);
            }
        }

        authenticator.fecharModais(driver);
        esperar(800);

        if (naTelaDeLogin(driver)) {
            log.warn("Sessao do TRC caiu — refazendo login");
            authenticator.login(driver);
            driver.get(baseUrl() + CAMINHO_LISTA);
            authenticator.fecharModais(driver);
        }
    }

    private void abrirEmAbaNova(WebDriver driver) {
        String anterior = driver.getWindowHandle();
        driver.switchTo().newWindow(WindowType.TAB);
        driver.get(baseUrl() + CAMINHO_LISTA);

        try {
            driver.switchTo().window(anterior).close();
        } catch (Exception ignorado) {

        }

        driver.switchTo().window(driver.getWindowHandles().iterator().next());
    }

    private boolean naTelaDeLogin(WebDriver driver) {
        String url = String.valueOf(driver.getCurrentUrl()).toLowerCase();
        return url.contains("login") || url.endsWith("trcmobile.com.br/");
    }

    private void marcarInativos(WebDriver driver) {
        executar(driver,
                "var sel = document.getElementById('chk_inativo');"
                        + "if (!sel) { return false; }"
                        + "sel.value = '0';"
                        + "sel.dispatchEvent(new Event('change', {bubbles:true}));"
                        + "return true;");
        esperar(2500);
        authenticator.fecharModais(driver);
    }

    private boolean aguardarTabela(WebDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(25))
                    .until(ExpectedConditions.presenceOfElementLocated(TABELA));
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(d -> !d.findElements(LINHAS).isEmpty());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private int capturarLinhas(WebDriver driver) {
        Object bruto = executar(driver,
                NORMALIZADORES_JS
                        + "var linhas = document.querySelectorAll('#table1 tbody tr');"
                        + "var saida = [];"
                        + "for (var i = 0; i < linhas.length; i++) {"
                        + "  var c = linhas[i].cells;"
                        + "  if (!c || c.length < 6) { continue; }"
                        + "  var link = linhas[i].querySelector(\"a[href*='" + TOKEN_EDICAO + "']\");"
                        + "  if (!link) { continue; }"
                        + "  var href = link.getAttribute('href') || '';"
                        + "  var id = href.substring(href.lastIndexOf('/') + 1);"
                        + "  function limpar(t) { return (t||'').replace(/\\u00a0/g,' ')"
                        + "    .replace(/\\s+/g,' ').trim(); }"
                        + "  saida.push({"
                        + "    id: id,"
                        + "    chave: norm(c[0].textContent),"
                        + "    nome: limpar(c[0].textContent),"
                        + "    funcao: limpar(c[2].textContent),"
                        + "    empreiteira: limpar(c[3].textContent),"
                        + "    cpf: limpar(c[4].textContent).replace(/\\D/g,'')"
                        + "  });"
                        + "}"
                        + "return saida;");

        if (!(bruto instanceof List<?> lista)) {
            return 0;
        }

        int novas = 0;

        for (Object item : (List<Object>) lista) {
            if (!(item instanceof Map<?, ?> mapa)) {
                continue;
            }

            String chave = texto(mapa.get("chave"));

            if (chave.isBlank()) {
                continue;
            }

            listagem.put(chave, new LinhaTrc(
                    texto(mapa.get("id")),
                    texto(mapa.get("nome")),
                    texto(mapa.get("funcao")),
                    texto(mapa.get("empreiteira")),
                    texto(mapa.get("cpf"))));
            novas++;
        }

        return novas;
    }

    private Map<String, String> lerComplemento(WebDriver driver, String id, String nome) {
        Map<String, String> dados = new LinkedHashMap<>();

        for (int tentativa = 1; tentativa <= 2; tentativa++) {
            try {
                driver.get(baseUrl() + "/home/" + TOKEN_EDICAO + "/" + id);
                authenticator.fecharModais(driver);
                esperar(1000);

                if (driver.findElements(By.id("nome_funcionario")).isEmpty()) {
                    log.warn("Ficha de '{}' nao abriu (tentativa {}/2)", nome, tentativa);
                    continue;
                }

                dados.put("nome", ler(driver, IDS_NOME, "Nome"));
                dados.put("cpf", ler(driver, IDS_CPF, "CPF").replaceAll("\\D", ""));
                dados.put("funcao", ler(driver, IDS_FUNCAO, "Funcao"));
                dados.put("rg", ler(driver, IDS_RG, "RG"));
                dados.put("nascimento", ler(driver, IDS_NASCIMENTO, "Data Nascimento"));
                dados.put("telefone", ler(driver, IDS_TELEFONE, "Telefone"));
                dados.put("email", ler(driver, IDS_EMAIL, "Email"));
                dados.put("entrada", ler(driver, IDS_ENTRADA, "Entrada na Obra"));

                conferirSituacao(driver, nome);
                return dados;

            } catch (Exception e) {
                log.warn("Erro ao abrir a ficha de '{}': {}", nome, e.getMessage());
            }
        }

        log.warn("Segui sem os dados complementares de '{}' (RG, nascimento, contato)", nome);
        return dados;
    }

    private void conferirEmpreiteira(String nomeBusca, String esperada, String naListagem) {
        if (naListagem == null || naListagem.isBlank() || esperada == null || esperada.isBlank()) {
            return;
        }

        String listada = NormalizadorNome.normalizar(naListagem);
        String alvo = NormalizadorNome.normalizar(esperada);

        boolean confere = listada.startsWith(alvo) || alvo.startsWith(listada)
                || listada.contains(alvo) || alvo.contains(listada);

        if (!confere) {
            log.warn("DIVERGENCIA em '{}': relatorio diz '{}', TRC diz '{}' — possivel homonimo",
                    nomeBusca, esperada, naListagem);
        }
    }

    private void conferirSituacao(WebDriver driver, String nome) {
        if (lerFlag(driver, ID_CHECK_DESLIGADO)) {
            log.warn("'{}' esta marcado como DESLIGADO da empreiteira no TRC", nome);
        }
        if (!lerFlag(driver, ID_CHECK_ATIVO)) {
            log.warn("'{}' esta marcado como INATIVO no TRC", nome);
        }
    }

    private boolean lerFlag(WebDriver driver, String id) {
        Object resultado = executar(driver,
                "var el = document.getElementById(arguments[0]);"
                        + "return el ? !!el.checked : null;",
                id);

        return Boolean.TRUE.equals(resultado);
    }

    private String ler(WebDriver driver, List<String> candidatos, String rotulo) {
        Object resultado = executar(driver,
                NORMALIZADORES_JS
                        + "var cands = arguments[0];"
                        + "var rotulo = norm(arguments[1]);"
                        + "function limpar(t) { return (t||'').replace(/\\u00a0/g,' ')"
                        + "  .replace(/\\s+/g,' ').trim(); }"
                        + "function valorDe(el) {"
                        + "  if (!el) { return null; }"
                        + "  if (el.tagName === 'SELECT') {"
                        + "    if (el.value === '0' || el.value === '') { return ''; }"
                        + "    var op = el.options[el.selectedIndex];"
                        + "    if (!op) { return ''; }"
                        + "    var t = limpar(op.textContent);"
                        + "    if (norm(t).indexOf('SELECIONE') === 0) { return ''; }"
                        + "    return t;"
                        + "  }"
                        + "  if (el.type === 'checkbox' || el.type === 'radio') {"
                        + "    return el.checked ? 'true' : '';"
                        + "  }"
                        + "  return limpar(el.value);"
                        + "}"
                        + "for (var i = 0; i < cands.length; i++) {"
                        + "  var el = document.getElementById(cands[i]);"
                        + "  if (!el) { el = document.getElementsByName(cands[i])[0]; }"
                        + "  var v = valorDe(el);"
                        + "  if (v !== null && v !== '') { return v; }"
                        + "}"
                        + "var labels = document.querySelectorAll('label');"
                        + "for (var i = 0; i < labels.length; i++) {"
                        + "  if (norm(labels[i].textContent).indexOf(rotulo) < 0) { continue; }"
                        + "  var alvo = null;"
                        + "  if (labels[i].htmlFor) { alvo = document.getElementById(labels[i].htmlFor); }"
                        + "  if (!alvo) {"
                        + "    var pai = labels[i].parentElement;"
                        + "    alvo = pai ? pai.querySelector('input, select, textarea') : null;"
                        + "  }"
                        + "  var v = valorDe(alvo);"
                        + "  if (v !== null && v !== '') { return v; }"
                        + "}"
                        + "return '';",
                candidatos, rotulo);

        return texto(resultado).trim();
    }

    private LocalDate data(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        String limpo = texto.trim();

        try {
            return LocalDate.parse(limpo, BR);
        } catch (Exception ignorado) {
            try {
                return LocalDate.parse(limpo);
            } catch (Exception tambemIgnorado) {
                return null;
            }
        }
    }

    public List<LinhaTrc> emCache() {
        return new ArrayList<>(listagem.values());
    }

    private String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private Object executar(WebDriver driver, String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    private String baseUrl() {
        String url = properties.url();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private void esperar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}