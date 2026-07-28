package com.cury.automacaocatraca.cfobras.page;

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

@Component
public class CompletarCadastrosPage {

    private static final Logger log = LoggerFactory.getLogger(CompletarCadastrosPage.class);

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final By SECAO = By.id("secao-completar-cadastros");
    private static final By LISTA = By.id("listaPendentesCompletar");
    private static final By VAZIO = By.id("vazioCompletar");
    private static final By NOME = By.id("inpNomeComp");
    private static final By STATUS = By.id("statusCompletar");
    private static final By SALVAR = By.id("btnSalvarProximoCompletar");
    private static final By PULAR = By.id("btnPularCompletar");
    private static final By PROGRESSO = By.id("progressoTxtCompletar");

    public record Pendente(String id, String nome, String empreiteiro, String faltando) {}

    private final CfObrasNavigator navigator;

    public CompletarCadastrosPage(CfObrasNavigator navigator) {
        this.navigator = navigator;
    }

    public void abrir(WebDriver driver) {
        navigator.abrirAba(driver, "completar-cadastros");

        try {
            new WebDriverWait(driver, Duration.ofSeconds(20))
                    .until(ExpectedConditions.visibilityOfElementLocated(SECAO));
        } catch (Exception e) {
            log.warn("Secao de completar cadastros nao apareceu");
        }

        esperar(1200);
    }

    public boolean filaVazia(WebDriver driver) {
        return driver.findElements(VAZIO).stream().anyMatch(WebElement::isDisplayed);
    }

    public String progresso(WebDriver driver) {
        List<WebElement> texto = driver.findElements(PROGRESSO);
        return texto.isEmpty() ? "" : texto.get(0).getText().trim();
    }

    @SuppressWarnings("unchecked")
    public List<Pendente> listar(WebDriver driver) {
        List<Pendente> saida = new ArrayList<>();

        Object bruto = executar(driver,
                "var itens = document.querySelectorAll('#listaPendentesCompletar .pendente-item');"
                        + "var saida = [];"
                        + "for (var i = 0; i < itens.length; i++) {"
                        + "  var oc = itens[i].getAttribute('onclick') || '';"
                        + "  var m = oc.match(/abrirFuncionarioCompletar\\(\\s*['\\\"]([^'\\\"]+)['\\\"]/);"
                        + "  if (!m) { continue; }"
                        + "  var nome = itens[i].querySelector('.pendente-nome');"
                        + "  var meta = itens[i].querySelector('.pendente-meta');"
                        + "  var textoMeta = meta ? meta.textContent.trim() : '';"
                        + "  var partes = textoMeta.split('\\u00b7');"
                        + "  saida.push({"
                        + "    id: m[1],"
                        + "    nome: nome ? nome.textContent.trim() : '',"
                        + "    empreiteiro: partes.length > 0 ? partes[0].trim() : '',"
                        + "    faltando: partes.length > 1 ? partes[1].replace('falta','').trim() : ''"
                        + "  });"
                        + "}"
                        + "return saida;");

        if (!(bruto instanceof List<?> lista)) {
            return saida;
        }

        for (Object item : (List<Object>) lista) {
            if (item instanceof Map<?, ?> mapa) {
                saida.add(new Pendente(
                        texto(mapa.get("id")),
                        texto(mapa.get("nome")),
                        texto(mapa.get("empreiteiro")),
                        texto(mapa.get("faltando"))));
            }
        }

        return saida;
    }

    public boolean selecionar(WebDriver driver, Pendente pendente) {
        Object abriu = executar(driver,
                "var itens = document.querySelectorAll('#listaPendentesCompletar .pendente-item');"
                        + "for (var i = 0; i < itens.length; i++) {"
                        + "  var oc = itens[i].getAttribute('onclick') || '';"
                        + "  if (oc.indexOf(arguments[0]) >= 0) {"
                        + "    itens[i].scrollIntoView({block:'center'});"
                        + "    itens[i].click();"
                        + "    return true;"
                        + "  }"
                        + "}"
                        + "return false;",
                pendente.id());

        if (!Boolean.TRUE.equals(abriu)) {
            log.warn("Nao achei '{}' na fila para abrir", pendente.nome());
            return false;
        }

        esperar(900);
        return !driver.findElements(NOME).isEmpty();
    }

    public void completar(WebDriver driver, DadosCadastroFuncionario dados) {
        seVazio(driver, "inpNomeComp", dados.nomeCompleto());
        seVazio(driver, "inpEmailComp", dados.email());
        seVazio(driver, "inpCPFComp", dados.cpf());
        seVazio(driver, "inpTelefoneComp", dados.telefone());
        seVazio(driver, "inpRgComp", dados.rg());

        seVazioData(driver, "inpDataNascComp", dados.dataNascimento());
        seVazioData(driver, "inpDataAdmissaoComp", dados.dataAdmissao());

        selecionarFuncao(driver, dados.funcao());
    }

    private void seVazio(WebDriver driver, String id, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }

        executar(driver,
                "var el = document.getElementById(arguments[0]);"
                        + "if (!el) { return false; }"
                        + "if ((el.value || '').trim() !== '') { return false; }"
                        + "el.focus();"
                        + "el.value = arguments[1];"
                        + "el.dispatchEvent(new Event('input', {bubbles:true}));"
                        + "el.dispatchEvent(new Event('change', {bubbles:true}));"
                        + "el.dispatchEvent(new Event('blur', {bubbles:true}));"
                        + "return true;",
                id, valor);
    }

    private void seVazioData(WebDriver driver, String id, LocalDate data) {
        if (data == null) {
            return;
        }

        seVazio(driver, id, data.format(ISO));
    }

    private void selecionarFuncao(WebDriver driver, String funcaoTrc) {
        if (funcaoTrc == null || funcaoTrc.isBlank()) {
            return;
        }

        Object escolhida = executar(driver,
                "function norm(t){ return (t||'').replace(/\\u00a0/g,' ').normalize('NFD')"
                        + "  .replace(/[\\u0300-\\u036f]/g,'')"
                        + "  .toUpperCase().replace(/\\s+/g,' ').trim(); }"
                        + "function compact(t){ return norm(t).replace(/[^A-Z0-9]/g,''); }"
                        + "var sel = document.getElementById('selFuncaoComp');"
                        + "if (!sel) { return ''; }"
                        + "if ((sel.value || '') !== '') { return sel.value; }"
                        + "var alvo = norm(arguments[0]);"
                        + "var alvoC = compact(arguments[0]);"
                        + "var opts = sel.options;"
                        + "var escolha = null;"
                        + "for (var i = 0; i < opts.length; i++) {"
                        + "  if (opts[i].value && norm(opts[i].value) === alvo) { escolha = i; break; }"
                        + "}"
                        + "if (escolha === null) {"
                        + "  for (var i = 0; i < opts.length; i++) {"
                        + "    if (opts[i].value && compact(opts[i].value) === alvoC) { escolha = i; break; }"
                        + "  }"
                        + "}"
                        + "if (escolha === null && alvoC.length > 3) {"
                        + "  var melhor = null, tam = 0;"
                        + "  for (var i = 0; i < opts.length; i++) {"
                        + "    var c = compact(opts[i].value);"
                        + "    if (c.length < 4) { continue; }"
                        + "    if ((c.indexOf(alvoC) >= 0 || alvoC.indexOf(c) >= 0) && c.length > tam) {"
                        + "      melhor = i; tam = c.length;"
                        + "    }"
                        + "  }"
                        + "  escolha = melhor;"
                        + "}"
                        + "if (escolha === null) {"
                        + "  for (var i = 0; i < opts.length; i++) {"
                        + "    if (opts[i].value === 'Outros') { escolha = i; break; }"
                        + "  }"
                        + "}"
                        + "if (escolha === null) { return ''; }"
                        + "sel.selectedIndex = escolha;"
                        + "sel.dispatchEvent(new Event('change', {bubbles:true}));"
                        + "return opts[escolha].value;",
                funcaoTrc);

        String valor = String.valueOf(escolhida);

        if ("Outros".equals(valor)) {
            log.warn("Funcao '{}' sem correspondente — caiu em 'Outros'", funcaoTrc);
        }
    }

    public String salvarEProximo(WebDriver driver) {
        return clicar(driver, SALVAR, "salvar");
    }

    public String pular(WebDriver driver) {
        return clicar(driver, PULAR, "pular");
    }

    private String clicar(WebDriver driver, By botao, String acao) {
        List<WebElement> botoes = driver.findElements(botao);

        if (botoes.isEmpty()) {
            return "ERRO: botao de " + acao + " nao encontrado";
        }

        WebElement alvo = botoes.get(0);
        executar(driver, "arguments[0].scrollIntoView({block:'center'});", alvo);

        try {
            alvo.click();
        } catch (Exception e) {
            executar(driver, "arguments[0].click();", alvo);
        }

        esperar(1800);

        List<WebElement> status = driver.findElements(STATUS);
        String mensagem = status.isEmpty() ? "" : status.get(0).getText().trim();

        return mensagem.isBlank() ? "(sem mensagem)" : mensagem;
    }

    public boolean temLista(WebDriver driver) {
        return !driver.findElements(LISTA).isEmpty();
    }

    private String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
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
}