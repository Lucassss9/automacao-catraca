package com.cury.automacaocatraca.cfobras.page;

import com.cury.automacaocatraca.cfobras.service.SenhaFuncionarioService;
import com.cury.automacaocatraca.domain.dto.DadosCadastroFuncionario;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CadastroFuncionarioPage {

    private static final Logger log = LoggerFactory.getLogger(CadastroFuncionarioPage.class);

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String FUNCAO_FALLBACK = "Outros";
    private static final String STATUS_PADRAO = "ativo";

    private static final By SECAO = By.id("secaoFuncionarios");
    private static final By NOME = By.id("inpNomeFuncionario");
    private static final By CPF = By.id("inpCPFFuncionario");
    private static final By RG = By.id("inpRgFuncionario");
    private static final By TELEFONE = By.id("inpTelefoneFuncionario");
    private static final By EMAIL = By.id("inpEmailFuncionario");
    private static final By BOTAO_SALVAR = By.id("btnSalvarFuncionario");
    private static final By BOTAO_FECHAR = By.id("btnFecharFuncionarios");
    private static final By STATUS = By.id("statusFuncionarios");
    private static final By PROMPT_SENHA = By.id("cfPromptInput");
    private static final By PROMPT_OK = By.id("cfPromptOk");

    private static final String NORMALIZADORES_JS =
            "function norm(t){ return (t||'').normalize('NFD').replace(/[\\u0300-\\u036f]/g,'')"
                    + "  .toUpperCase().replace(/\\s+/g,' ').trim(); }"
                    + "function compact(t){ return norm(t).replace(/[^A-Z0-9]/g,''); }";

    private static final int TENTATIVAS_DE_CORRECAO = 3;

    private final CfObrasNavigator navigator;
    private final SenhaFuncionarioService senhaService;

    public CadastroFuncionarioPage(CfObrasNavigator navigator, SenhaFuncionarioService senhaService) {
        this.navigator = navigator;
        this.senhaService = senhaService;
    }

    public void abrir(WebDriver driver) {
        navigator.abrirAba(driver, "empreiteiros");
        esperar(1200);
    }

    public Optional<String> abrirFuncionariosDe(WebDriver driver, String nomeEmpreiteira) {
        Object resultado = executar(driver,
                NORMALIZADORES_JS
                        + "var alvo = norm(arguments[0]);"
                        + "var alvoC = compact(arguments[0]);"
                        + "var cards = document.querySelectorAll('#listaEmpreiteiros .empreiteiro-card');"
                        + "var exato = null, compacto = null, parcial = null;"
                        + "for (var i = 0; i < cards.length; i++) {"
                        + "  if (!cards[i].querySelector(\"[onclick*='gerenciarFuncionarios']\")) { continue; }"
                        + "  var nomeEl = cards[i].querySelector('.empreiteiro-nome');"
                        + "  var razaoEl = cards[i].querySelector('.empreiteiro-razao');"
                        + "  var textos = [nomeEl ? nomeEl.textContent : '', razaoEl ? razaoEl.textContent : ''];"
                        + "  for (var j = 0; j < textos.length; j++) {"
                        + "    var n = norm(textos[j]);"
                        + "    var c = compact(textos[j]);"
                        + "    if (!c) { continue; }"
                        + "    if (n === alvo) { exato = i; break; }"
                        + "    if (compacto === null && c === alvoC) { compacto = i; }"
                        + "    if (parcial === null && c.length > 5 && alvoC.length > 5"
                        + "        && (c.indexOf(alvoC) >= 0 || alvoC.indexOf(c) >= 0)) { parcial = i; }"
                        + "  }"
                        + "  if (exato !== null) { break; }"
                        + "}"
                        + "var idx = exato !== null ? exato : (compacto !== null ? compacto : parcial);"
                        + "if (idx === null) { return { achou: false, total: cards.length }; }"
                        + "var btn = cards[idx].querySelector(\"[onclick*='gerenciarFuncionarios']\");"
                        + "btn.scrollIntoView({block:'center'});"
                        + "btn.click();"
                        + "var oc = btn.getAttribute('onclick') || '';"
                        + "var m = oc.match(/gerenciarFuncionarios\\(\\s*['\\\"]([^'\\\"]+)['\\\"]/);"
                        + "var nomeEl = cards[idx].querySelector('.empreiteiro-nome');"
                        + "return {"
                        + "  achou: true,"
                        + "  id: m ? m[1] : '',"
                        + "  tipo: exato !== null ? 'exato' : (compacto !== null ? 'compacto' : 'parcial'),"
                        + "  nomeCard: nomeEl ? nomeEl.textContent.trim() : '',"
                        + "  total: cards.length"
                        + "};",
                nomeEmpreiteira);

        Map<?, ?> mapa = (Map<?, ?>) resultado;

        if (!Boolean.TRUE.equals(mapa.get("achou"))) {
            log.warn("Empreiteira '{}' sem card no CF Obras ({} cards na tela)",
                    nomeEmpreiteira, mapa.get("total"));
            return Optional.empty();
        }

        try {
            new WebDriverWait(driver, Duration.ofSeconds(20))
                    .until(ExpectedConditions.visibilityOfElementLocated(SECAO));
        } catch (Exception e) {
            log.warn("Secao de funcionarios nao apareceu para '{}'", nomeEmpreiteira);
            return Optional.empty();
        }

        aguardarListaCarregar(driver, String.valueOf(mapa.get("nomeCard")));

        log.info("Funcionarios de '{}' abertos por match {} (card '{}', id {})",
                nomeEmpreiteira, mapa.get("tipo"), mapa.get("nomeCard"), mapa.get("id"));

        return Optional.of(String.valueOf(mapa.get("id")));
    }

    private void aguardarListaCarregar(WebDriver driver, String nomeCard) {
        esperar(800);

        for (int tentativa = 1; tentativa <= 16; tentativa++) {
            Object estado = executar(driver,
                    "var lista = document.getElementById('listaFuncionarios');"
                            + "if (!lista) { return 'sem lista'; }"
                            + "if (lista.querySelectorAll('.funcionario-card').length > 0) { return 'com cards'; }"
                            + "return (lista.textContent || '').trim() ? 'vazia declarada' : 'carregando';");

            String texto = String.valueOf(estado);

            if (!"carregando".equals(texto)) {
                log.debug("Lista de funcionarios de '{}': {}", nomeCard, texto);
                esperar(400);
                return;
            }

            esperar(500);
        }

        log.warn("Lista de funcionarios de '{}' nao terminou de carregar — "
                + "vou tratar como vazia, confira se houve duplicata", nomeCard);
    }

    public boolean jaCadastrado(WebDriver driver, String nome, String cpf) {
        Object resultado = executar(driver,
                NORMALIZADORES_JS
                        + "var alvo = norm(arguments[0]);"
                        + "var cpfAlvo = (arguments[1] || '').replace(/\\D/g,'');"
                        + "var cards = document.querySelectorAll('#listaFuncionarios .funcionario-card');"
                        + "for (var i = 0; i < cards.length; i++) {"
                        + "  var nomeEl = cards[i].querySelector('.funcionario-nome');"
                        + "  if (nomeEl && norm(nomeEl.textContent) === alvo) { return true; }"
                        + "  if (cpfAlvo.length === 11) {"
                        + "    var digitos = (cards[i].textContent || '').replace(/\\D/g,'');"
                        + "    if (digitos.indexOf(cpfAlvo) >= 0) { return true; }"
                        + "  }"
                        + "}"
                        + "return false;",
                nome, cpf);

        return Boolean.TRUE.equals(resultado);
    }

    public int totalNaLista(WebDriver driver) {
        Object total = executar(driver,
                "return document.querySelectorAll('#listaFuncionarios .funcionario-card').length;");
        return total == null ? 0 : Integer.parseInt(String.valueOf(total));
    }

    public ResultadoCadastro cadastrar(WebDriver driver, DadosCadastroFuncionario dados) {
        new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(ExpectedConditions.visibilityOfElementLocated(NOME));

        int antes = totalNaLista(driver);

        preencher(driver, NOME, dados.nomeCompleto());
        preencher(driver, CPF, dados.cpf());
        preencher(driver, RG, dados.rg());
        preencher(driver, TELEFONE, dados.telefone());
        preencher(driver, EMAIL, dados.email());

        definirData(driver, "inpDataNascFuncionario", dados.dataNascimento());
        definirData(driver, "inpDataAdmissao", dados.dataAdmissao());

        String funcaoSelecionada = definirFuncao(driver, dados.funcao());
        definirSelect(driver, "selStatusFuncionario", STATUS_PADRAO);

        String nivel = dados.nivelAcesso().name().toLowerCase();
        String nivelSelecionado = definirSelect(driver, "selNivelAcessoFuncionario", nivel);

        if (!nivel.equals(nivelSelecionado)) {
            return new ResultadoCadastro(false,
                    "ERRO: nivel de acesso '" + nivel + "' nao aceito pelo select", funcaoSelecionada);
        }

        log.info("Preenchido '{}': funcao TRC '{}' -> CF Obras '{}' | nivel {}",
                dados.nomeCompleto(), dados.funcao(), funcaoSelecionada, nivel);

        List<String> faltando = conferirFormulario(driver);

        for (int tentativa = 1; tentativa <= TENTATIVAS_DE_CORRECAO && !faltando.isEmpty(); tentativa++) {
            log.warn("Formulario de '{}' incompleto: {} — corrigindo (tentativa {}/{})",
                    dados.nomeCompleto(), String.join(", ", faltando),
                    tentativa, TENTATIVAS_DE_CORRECAO);

            corrigir(driver, dados, nivel, faltando);
            faltando = conferirFormulario(driver);
        }

        if (!faltando.isEmpty()) {
            log.error("NAO SALVEI '{}': campos vazios: {}",
                    dados.nomeCompleto(), String.join(", ", faltando));
            return new ResultadoCadastro(false,
                    "ERRO: formulario incompleto — faltou " + String.join(", ", faltando),
                    funcaoSelecionada);
        }

        WebElement salvar = driver.findElement(BOTAO_SALVAR);
        executar(driver, "arguments[0].scrollIntoView({block:'center'});", salvar);
        esperar(400);

        try {
            salvar.click();
        } catch (Exception e) {
            executar(driver, "arguments[0].click();", salvar);
        }

        responderPromptDeSenha(driver, dados);

        String mensagem = aguardarResultado(driver, dados.nomeCompleto(), antes);

        return new ResultadoCadastro(indicaSucesso(mensagem), mensagem, funcaoSelecionada);
    }

    private void corrigir(WebDriver driver, DadosCadastroFuncionario dados,
                          String nivel, List<String> faltando) {
        if (faltando.contains("nome")) {
            preencher(driver, NOME, dados.nomeCompleto());
        }
        if (faltando.contains("CPF")) {
            preencher(driver, CPF, dados.cpf());
        }
        if (faltando.contains("telefone")) {
            preencher(driver, TELEFONE, dados.telefone());
        }
        if (faltando.contains("email")) {
            preencher(driver, EMAIL, dados.email());
        }
        if (faltando.contains("funcao")) {
            definirFuncao(driver, dados.funcao());
        }
        if (faltando.contains("status")) {
            definirSelect(driver, "selStatusFuncionario", STATUS_PADRAO);
        }
        if (faltando.contains("nivel de acesso")) {
            definirSelect(driver, "selNivelAcessoFuncionario", nivel);
        }

        esperar(500);
    }

    private List<String> conferirFormulario(WebDriver driver) {
        List<String> faltando = new ArrayList<>();

        Map<String, String> campos = Map.of(
                "inpNomeFuncionario", "nome",
                "inpCPFFuncionario", "CPF",
                "inpTelefoneFuncionario", "telefone",
                "inpEmailFuncionario", "email",
                "selFuncaoFuncionario", "funcao",
                "selStatusFuncionario", "status",
                "selNivelAcessoFuncionario", "nivel de acesso");

        for (Map.Entry<String, String> campo : campos.entrySet()) {
            Object valor = executar(driver,
                    "var el = document.getElementById(arguments[0]);"
                            + "return el ? (el.value || '').trim() : '';",
                    campo.getKey());

            if (valor == null || String.valueOf(valor).isBlank()) {
                faltando.add(campo.getValue());
            }
        }

        return faltando;
    }

    private void responderPromptDeSenha(WebDriver driver, DadosCadastroFuncionario dados) {
        WebElement campo;

        try {
            campo = new WebDriverWait(driver, Duration.ofSeconds(8))
                    .until(ExpectedConditions.visibilityOfElementLocated(PROMPT_SENHA));
        } catch (Exception e) {
            return;
        }

        String senha = senhaService.gerar(dados.cpf());

        try {
            campo.clear();
            campo.sendKeys(senha);
        } catch (Exception e) {
            executar(driver,
                    "var el = document.getElementById('cfPromptInput');"
                            + "if (el) {"
                            + "  el.value = arguments[0];"
                            + "  el.dispatchEvent(new Event('input', {bubbles:true}));"
                            + "  el.dispatchEvent(new Event('change', {bubbles:true}));"
                            + "}",
                    senha);
        }

        List<WebElement> ok = driver.findElements(PROMPT_OK);

        if (ok.isEmpty()) {
            log.warn("Prompt de senha apareceu para '{}' mas sem botao OK", dados.nomeCompleto());
            return;
        }

        try {
            ok.get(0).click();
        } catch (Exception e) {
            executar(driver, "arguments[0].click();", ok.get(0));
        }

        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.invisibilityOfElementLocated(PROMPT_SENHA));
        } catch (Exception e) {
            log.warn("Prompt de senha nao fechou para '{}'", dados.nomeCompleto());
        }

        log.info("Senha definida para '{}' ({})", dados.nomeCompleto(), senhaService.descricao());
    }

    public void fechar(WebDriver driver) {
        List<WebElement> botoes = driver.findElements(BOTAO_FECHAR);

        if (botoes.isEmpty()) {
            return;
        }

        try {
            botoes.get(0).click();
        } catch (Exception e) {
            executar(driver, "arguments[0].click();", botoes.get(0));
        }

        esperar(600);
    }

    public boolean caiuNoFallback(String funcaoSelecionada) {
        return FUNCAO_FALLBACK.equals(funcaoSelecionada);
    }

    private String definirFuncao(WebDriver driver, String funcaoTrc) {
        Object resultado = executar(driver,
                NORMALIZADORES_JS
                        + "var alvo = norm(arguments[0]);"
                        + "var alvoC = compact(arguments[0]);"
                        + "var fallback = arguments[1];"
                        + "var sel = document.getElementById('selFuncaoFuncionario');"
                        + "if (!sel) { return 'sem select'; }"
                        + "var opts = sel.options;"
                        + "var escolha = null;"
                        + "for (var i = 0; i < opts.length; i++) {"
                        + "  if (!opts[i].value) { continue; }"
                        + "  if (norm(opts[i].value) === alvo) { escolha = i; break; }"
                        + "}"
                        + "if (escolha === null) {"
                        + "  for (var i = 0; i < opts.length; i++) {"
                        + "    if (opts[i].value && compact(opts[i].value) === alvoC) { escolha = i; break; }"
                        + "  }"
                        + "}"
                        + "if (escolha === null && alvoC.length > 3) {"
                        + "  var melhor = null, melhorTam = 0;"
                        + "  for (var i = 0; i < opts.length; i++) {"
                        + "    var c = compact(opts[i].value);"
                        + "    if (c.length < 4) { continue; }"
                        + "    if ((c.indexOf(alvoC) >= 0 || alvoC.indexOf(c) >= 0) && c.length > melhorTam) {"
                        + "      melhor = i; melhorTam = c.length;"
                        + "    }"
                        + "  }"
                        + "  escolha = melhor;"
                        + "}"
                        + "if (escolha === null) {"
                        + "  for (var i = 0; i < opts.length; i++) {"
                        + "    if (opts[i].value === fallback) { escolha = i; break; }"
                        + "  }"
                        + "}"
                        + "if (escolha === null) { return 'nenhuma opcao'; }"
                        + "sel.selectedIndex = escolha;"
                        + "sel.dispatchEvent(new Event('change', {bubbles:true}));"
                        + "return opts[escolha].value;",
                funcaoTrc, FUNCAO_FALLBACK);

        String escolhida = String.valueOf(resultado);

        if (FUNCAO_FALLBACK.equals(escolhida)) {
            log.warn("Funcao '{}' do TRC sem correspondente no CF Obras -> caiu em '{}'",
                    funcaoTrc, FUNCAO_FALLBACK);
        }

        return escolhida;
    }

    private String definirSelect(WebDriver driver, String idSelect, String value) {
        Object resultado = executar(driver,
                "var sel = document.getElementById(arguments[0]);"
                        + "if (!sel) { return ''; }"
                        + "for (var i = 0; i < sel.options.length; i++) {"
                        + "  if (sel.options[i].value === arguments[1]) {"
                        + "    sel.selectedIndex = i;"
                        + "    sel.dispatchEvent(new Event('change', {bubbles:true}));"
                        + "    return sel.options[i].value;"
                        + "  }"
                        + "}"
                        + "return '';",
                idSelect, value);

        return String.valueOf(resultado);
    }

    private void definirData(WebDriver driver, String idCampo, LocalDate data) {
        if (data == null) {
            return;
        }

        executar(driver,
                "var el = document.getElementById(arguments[0]);"
                        + "if (!el) { return; }"
                        + "el.value = arguments[1];"
                        + "el.dispatchEvent(new Event('input', {bubbles:true}));"
                        + "el.dispatchEvent(new Event('change', {bubbles:true}));",
                idCampo, data.format(ISO));
    }

    private void preencher(WebDriver driver, By campo, String valor) {
        List<WebElement> elementos = driver.findElements(campo);

        if (elementos.isEmpty()) {
            return;
        }

        WebElement elemento = elementos.get(0);
        elemento.clear();

        if (valor != null && !valor.isBlank()) {
            elemento.sendKeys(valor);
        }
    }

    private String aguardarResultado(WebDriver driver, String nome, int antes) {
        long limite = System.currentTimeMillis() + 2500;

        while (System.currentTimeMillis() < limite) {
            if (!textoDoStatus(driver).isBlank()
                    || totalNaLista(driver) > antes
                    || campoVazio(driver, NOME)) {
                break;
            }

            esperar(150);
        }

        String mensagem = textoDoStatus(driver);
        int depois = totalNaLista(driver);
        boolean formularioLimpo = campoVazio(driver, NOME);

        log.info("Apos salvar '{}': status='{}' | lista {} -> {} | formulario limpo={}",
                nome, mensagem.isBlank() ? "(vazio)" : mensagem, antes, depois, formularioLimpo);

        if (!mensagem.isBlank()) {
            return mensagem;
        }

        if (depois > antes) {
            return "cadastrado (lista cresceu)";
        }

        return formularioLimpo ? "cadastrado (formulario limpo)" : "ERRO: nada mudou apos salvar";
    }

    private boolean indicaSucesso(String mensagem) {
        String texto = mensagem == null ? "" : mensagem.toLowerCase();

        if (texto.contains("erro") || texto.contains("obrigat")
                || texto.contains("falha") || texto.contains("invalid")
                || texto.contains("preencha") || texto.contains("duplicad")) {
            return false;
        }

        return texto.contains("sucesso") || texto.contains("cadastrad")
                || texto.contains("salvo") || texto.contains("lista cresceu")
                || texto.contains("formulario limpo");
    }

    private boolean campoVazio(WebDriver driver, By campo) {
        List<WebElement> elementos = driver.findElements(campo);

        if (elementos.isEmpty()) {
            return false;
        }

        String valor = elementos.get(0).getDomProperty("value");
        return valor == null || valor.isBlank();
    }

    private String textoDoStatus(WebDriver driver) {
        List<WebElement> status = driver.findElements(STATUS);
        return status.isEmpty() ? "" : status.get(0).getText().trim();
    }

    private Object executar(WebDriver driver, String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    private void esperar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record ResultadoCadastro(boolean sucesso, String mensagem, String funcaoSelecionada) {
    }
}