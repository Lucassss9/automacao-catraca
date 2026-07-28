package com.cury.automacaocatraca.trc;

import com.cury.automacaocatraca.config.ObraConfig;
import com.cury.automacaocatraca.config.TrcProperties;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Component
public class TrcReportExtractor {

    private static final Logger log = LoggerFactory.getLogger(TrcReportExtractor.class);

    private static final String ID_RELATORIO_CATRACA = "18";
    private static final DateTimeFormatter FORMATO_TELA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TENTATIVAS_PREPARO = 3;

    private static final By ITEM_RELATORIO = By.cssSelector("li[id_relatorio='" + ID_RELATORIO_CATRACA + "']");
    private static final By CAMPO_DATA_INICIAL = By.id("dt_inicial");
    private static final By BOTAO_GERAR = By.id("gerar");
    private static final By BOTAO_EXPORTAR = By.xpath(
            "//*[self::a or self::button or self::input]"
                    + "[contains(translate(., 'EXPORTAR', 'exportar'), 'exportar')"
                    + " or contains(translate(@value, 'EXPORTAR', 'exportar'), 'exportar')]");

    private final TrcProperties properties;
    private final TrcAuthenticator authenticator;
    private final TrcFileManager fileManager;
    private final int diasAtras;

    public TrcReportExtractor(TrcProperties properties,
                              TrcAuthenticator authenticator,
                              TrcFileManager fileManager,
                              @Value("${automacao.dias-atras:1}") int diasAtras) {
        this.properties = properties;
        this.authenticator = authenticator;
        this.fileManager = fileManager;
        this.diasAtras = Math.max(0, diasAtras);
    }

    public LocalDate dataDeReferencia() {
        return LocalDate.now().minusDays(diasAtras);
    }

    public Path extrairRelatorioDoDia(WebDriver driver, ObraConfig obra, Path pastaDownload) {
        LocalDate hoje = dataDeReferencia();

        log.info("Relatorio de {} (dias-atras={})", hoje, diasAtras);

        prepararTelaComRetentativa(driver, obra, hoje);

        garantirTodosSelecionados(driver, "empreiteira_sel", "selAllEm", "combo_empreiteira", "empreiteiras");
        garantirTodosSelecionados(driver, "funcionario_sel", "selAllFn", "combo_funcionario", "funcionarios");
        sincronizarHidden(driver, "obra_sel", "combo_obra", "obra");

        verificarSelecoes(driver, obra);

        Set<String> antes = fileManager.listarArquivos(pastaDownload);

        gerarRelatorio(driver);
        clicarExportar(driver);

        Path baixado = fileManager.aguardarNovoArquivo(pastaDownload, antes, Duration.ofSeconds(120));
        return fileManager.renomear(baixado, obra, hoje);
    }

    private void prepararTelaComRetentativa(WebDriver driver, ObraConfig obra, LocalDate data) {
        for (int tentativa = 1; tentativa <= TENTATIVAS_PREPARO; tentativa++) {
            log.info("Preparando a tela do relatorio (tentativa {}/{})", tentativa, TENTATIVAS_PREPARO);

            abrirTelaDoRelatorio(driver);
            diagnosticarTela(driver);
            preencherDatas(driver, data);
            selecionarObra(driver, obra);

            if (listaCarregou(driver, "empreiteira_sel", "empreiteiras", 30000)) {
                return;
            }

            log.warn("Lista de empreiteiras nao carregou na tentativa {} — recarregando a tela", tentativa);
            esperar(2000);
        }

        throw new RuntimeException(
                "A lista de empreiteiras nao carregou apos " + TENTATIVAS_PREPARO + " tentativas. "
                        + "Confira manualmente se a obra '" + obra.nomeTrc()
                        + "' tem empreiteiras no relatorio 03.1 do TRC.");
    }

    private void abrirTelaDoRelatorio(WebDriver driver) {
        log.info("Abrindo a tela de relatorios do TRC");
        driver.get(baseUrl() + "/Relatorio_Listar");
        authenticator.fecharModais(driver);

        WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(20));
        espera.until(ExpectedConditions.elementToBeClickable(ITEM_RELATORIO)).click();
        espera.until(ExpectedConditions.visibilityOfElementLocated(CAMPO_DATA_INICIAL));

        esperar(2500);
        log.info("Relatorio 03.1 - Frequencia - Catraca selecionado");
    }

    private void diagnosticarTela(WebDriver driver) {
        Object info = executar(driver,
                "function q(id){ var e = document.getElementById(id); return e ? e.options.length : -1; }"
                        + "return 'url=' + location.href"
                        + " + ' | obra_sel=' + q('obra_sel')"
                        + " + ' | empreiteira_sel=' + q('empreiteira_sel')"
                        + " + ' | funcionario_sel=' + q('funcionario_sel')"
                        + " + ' | jQuery=' + (window.jQuery ? 'sim' : 'nao');");

        log.info("DIAGNOSTICO TELA >>> {}", info);
    }

    private void preencherDatas(WebDriver driver, LocalDate data) {
        String texto = data.format(FORMATO_TELA);
        definirValor(driver, "dt_inicial", texto);
        definirValor(driver, "dt_final", texto);
        log.info("Periodo do relatorio: {} a {}", texto, texto);
    }

    private void selecionarObra(WebDriver driver, ObraConfig obra) {
        String alvo = NormalizadorNome.normalizar(obra.nomeTrc());

        Boolean achou = (Boolean) executar(driver,
                "var alvo = arguments[0];"
                        + "var sel = document.getElementById('obra_sel');"
                        + "if (!sel) { return false; }"
                        + "var achou = false;"
                        + "for (var i = 0; i < sel.options.length; i++) {"
                        + "  var t = sel.options[i].textContent.normalize('NFD').replace(/[\\u0300-\\u036f]/g,'')"
                        + "      .toUpperCase().replace(/\\s+/g,' ').trim();"
                        + "  var bate = (t === alvo);"
                        + "  sel.options[i].selected = bate;"
                        + "  if (bate) { achou = true; }"
                        + "}"
                        + "if (window.jQuery) {"
                        + "  jQuery(sel).trigger('chosen:updated');"
                        + "  jQuery(sel).trigger('change');"
                        + "} else {"
                        + "  sel.dispatchEvent(new Event('change', {bubbles:true}));"
                        + "}"
                        + "return achou;",
                alvo);

        if (!Boolean.TRUE.equals(achou)) {
            throw new RuntimeException(
                    "Obra '" + obra.nomeTrc() + "' nao existe na lista do relatorio 03.1 do TRC.");
        }

        log.info("Obra selecionada no relatorio: {}", obra.nomeTrc());

        fecharComboEClicarFora(driver);
        esperar(4000);
    }

    private void fecharComboEClicarFora(WebDriver driver) {
        executar(driver,
                "var sel = document.getElementById('obra_sel');"
                        + "if (!sel || !window.jQuery) { return; }"
                        + "var $s = jQuery(sel);"
                        + "$s.trigger('chosen:close');"
                        + "$s.trigger('chosen:updated');"
                        + "$s.trigger('blur');");

        clicarEmAreaNeutra(driver);

        executar(driver,
                "if (window.jQuery) {"
                        + "  jQuery(document).trigger('mousedown');"
                        + "  jQuery('body').trigger('click');"
                        + "} else {"
                        + "  document.body.dispatchEvent(new MouseEvent('mousedown', {bubbles:true}));"
                        + "  document.body.dispatchEvent(new MouseEvent('click', {bubbles:true}));"
                        + "}");

        log.info("Combo da obra fechado (clique fora simulado)");
    }

    private void clicarEmAreaNeutra(WebDriver driver) {
        List<WebElement> alvos = driver.findElements(
                By.cssSelector("h1, h2, h3, legend, .panel-heading, .content-header"));

        try {
            if (!alvos.isEmpty()) {
                new Actions(driver).moveToElement(alvos.get(0)).click().perform();
                return;
            }

            new Actions(driver)
                    .moveToElement(driver.findElement(By.tagName("body")), 10, 10)
                    .click()
                    .perform();

        } catch (Exception e) {
            log.debug("Clique nativo fora do combo falhou: {}", e.getMessage());
        }
    }

    private boolean listaCarregou(WebDriver driver, String idSelect, String descricao, long timeoutMillis) {
        long limite = System.currentTimeMillis() + timeoutMillis;
        long proximoRetrigger = System.currentTimeMillis() + 12000;

        while (System.currentTimeMillis() < limite) {
            long total = contarOpcoes(driver, idSelect);

            if (total > 0) {
                log.info("Lista de {} carregada ({} itens)", descricao, total);
                return true;
            }

            if (System.currentTimeMillis() > proximoRetrigger) {
                log.info("Lista de {} ainda vazia — redisparando o change e fechando o combo", descricao);
                executar(driver,
                        "var sel = document.getElementById('obra_sel');"
                                + "if (!sel) { return; }"
                                + "if (window.jQuery) { jQuery(sel).trigger('change'); }"
                                + "else { sel.dispatchEvent(new Event('change', {bubbles:true})); }");
                fecharComboEClicarFora(driver);
                proximoRetrigger = System.currentTimeMillis() + 12000;
            }

            esperar(700);
        }

        return false;
    }

    private void garantirTodosSelecionados(WebDriver driver, String idSelect, String idCheckbox,
                                           String idHidden, String descricao) {
        marcarCheckbox(driver, idCheckbox, descricao);
        esperar(2500);

        long selecionados = contarSelecionados(driver, idSelect);

        if (selecionados == 0) {
            log.warn("O checkbox de {} nao selecionou nada — forcando via JavaScript", descricao);
            selecionarTudoViaJavaScript(driver, idSelect);
            esperar(1500);
            selecionados = contarSelecionados(driver, idSelect);
        }

        if (selecionados == 0) {
            throw new RuntimeException("Nao consegui selecionar nenhuma opcao de " + descricao + ".");
        }

        sincronizarHidden(driver, idSelect, idHidden, descricao);

        log.info("Selecionadas {} de {} opcoes de {}", selecionados, contarOpcoes(driver, idSelect), descricao);
    }

    private void sincronizarHidden(WebDriver driver, String idSelect, String idHidden, String descricao) {
        Object valor = executar(driver,
                "var hid = document.getElementById(arguments[1]);"
                        + "if (!hid) { return '(sem hidden)'; }"
                        + "var sel = document.getElementById(arguments[0]);"
                        + "if (!sel) { return hid.value; }"
                        + "var vals = [];"
                        + "for (var i = 0; i < sel.selectedOptions.length; i++) {"
                        + "  vals.push(sel.selectedOptions[i].value);"
                        + "}"
                        + "if (vals.length > 0) {"
                        + "  hid.value = vals.join(',');"
                        + "  if (window.jQuery) { jQuery(hid).trigger('change'); }"
                        + "}"
                        + "return hid.value;",
                idSelect, idHidden);

        String texto = String.valueOf(valor);
        String resumo = texto.length() > 60 ? texto.substring(0, 60) + "..." : texto;
        log.info("Hidden '{}' ({}): {}", idHidden, descricao, resumo);
    }

    private void verificarSelecoes(WebDriver driver, ObraConfig obra) {
        long obrasSelecionadas = contarSelecionados(driver, "obra_sel");
        long empreiteirasSelecionadas = contarSelecionados(driver, "empreiteira_sel");
        long funcionariosSelecionados = contarSelecionados(driver, "funcionario_sel");

        boolean checkEmpreiteiras = checkboxMarcado(driver, "selAllEm");
        boolean checkFuncionarios = checkboxMarcado(driver, "selAllFn");
        String nomeObraSelecionada = nomeDaObraSelecionada(driver);

        log.info("VERIFICACAO >>> obra='{}' ({} selecionada) | empreiteiras={} (checkbox={}) | funcionarios={} (checkbox={})",
                nomeObraSelecionada, obrasSelecionadas,
                empreiteirasSelecionadas, checkEmpreiteiras,
                funcionariosSelecionados, checkFuncionarios);

        if (obrasSelecionadas == 0) {
            throw new RuntimeException("Nenhuma obra selecionada no relatorio.");
        }

        if (!NormalizadorNome.normalizar(nomeObraSelecionada)
                .equals(NormalizadorNome.normalizar(obra.nomeTrc()))) {
            throw new RuntimeException("Obra errada selecionada. Esperava '" + obra.nomeTrc()
                    + "', encontrei '" + nomeObraSelecionada + "'");
        }

        if (empreiteirasSelecionadas == 0) {
            throw new RuntimeException("Nenhuma empreiteira selecionada (checkbox marcado: " + checkEmpreiteiras + ").");
        }

        if (funcionariosSelecionados == 0) {
            throw new RuntimeException("Nenhum funcionario selecionado (checkbox marcado: " + checkFuncionarios + ").");
        }

        log.info("Verificacao OK — pronto para gerar o relatorio");
    }

    private boolean checkboxMarcado(WebDriver driver, String id) {
        List<WebElement> encontrados = driver.findElements(By.id(id));
        return !encontrados.isEmpty() && encontrados.get(0).isSelected();
    }

    private String nomeDaObraSelecionada(WebDriver driver) {
        Object nome = executar(driver,
                "var sel = document.getElementById('obra_sel');"
                        + "if (!sel || sel.selectedOptions.length === 0) { return '(nenhuma)'; }"
                        + "return sel.selectedOptions[0].textContent.trim();");
        return String.valueOf(nome);
    }

    private void marcarCheckbox(WebDriver driver, String idCheckbox, String descricao) {
        List<WebElement> checkboxes = driver.findElements(By.id(idCheckbox));

        if (checkboxes.isEmpty()) {
            log.warn("Checkbox '{}' nao encontrado", idCheckbox);
            return;
        }

        WebElement checkbox = checkboxes.get(0);

        if (checkbox.isSelected()) {
            log.info("Checkbox de todas as {} ja estava marcado", descricao);
            return;
        }

        try {
            executar(driver, "arguments[0].scrollIntoView({block:'center'});", checkbox);
            esperar(300);
            checkbox.click();
        } catch (Exception e) {
            executar(driver, "arguments[0].click();", checkbox);
        }
    }

    private void selecionarTudoViaJavaScript(WebDriver driver, String idSelect) {
        executar(driver,
                "var sel = document.getElementById(arguments[0]);"
                        + "if (!sel) { return; }"
                        + "for (var i = 0; i < sel.options.length; i++) { sel.options[i].selected = true; }"
                        + "if (window.jQuery) {"
                        + "  jQuery(sel).trigger('chosen:updated');"
                        + "  jQuery(sel).trigger('change');"
                        + "} else {"
                        + "  sel.dispatchEvent(new Event('change', {bubbles:true}));"
                        + "}",
                idSelect);
    }

    private long contarOpcoes(WebDriver driver, String idSelect) {
        Object total = executar(driver,
                "var sel = document.getElementById(arguments[0]);"
                        + "return sel ? sel.options.length : 0;",
                idSelect);
        return ((Number) total).longValue();
    }

    private long contarSelecionados(WebDriver driver, String idSelect) {
        Object total = executar(driver,
                "var sel = document.getElementById(arguments[0]);"
                        + "return sel ? sel.selectedOptions.length : 0;",
                idSelect);
        return ((Number) total).longValue();
    }

    private void gerarRelatorio(WebDriver driver) {
        log.info("Clicando em Gerar Relatorio");

        WebElement botao = driver.findElement(BOTAO_GERAR);
        executar(driver, "arguments[0].scrollIntoView({block:'center'});", botao);
        esperar(300);

        try {
            botao.click();
        } catch (Exception e) {
            executar(driver, "arguments[0].click();", botao);
        }

        esperar(2000);
        verificarAlerta(driver);

        new WebDriverWait(driver, Duration.ofSeconds(180))
                .until(d -> d.findElements(BOTAO_EXPORTAR).stream().anyMatch(WebElement::isDisplayed));

        log.info("Relatorio gerado");
    }

    private void verificarAlerta(WebDriver driver) {
        try {
            Alert alerta = driver.switchTo().alert();
            String texto = alerta.getText();
            alerta.accept();
            throw new RuntimeException("O TRC recusou a geracao do relatorio: " + texto);
        } catch (NoAlertPresentException e) {
            log.debug("Nenhum alerta apos gerar");
        }
    }

    private void clicarExportar(WebDriver driver) {
        List<WebElement> botoes = driver.findElements(BOTAO_EXPORTAR);

        for (WebElement botao : botoes) {
            if (!botao.isDisplayed()) {
                continue;
            }

            executar(driver, "arguments[0].scrollIntoView({block:'center'});", botao);
            esperar(500);

            try {
                botao.click();
            } catch (Exception e) {
                executar(driver, "arguments[0].click();", botao);
            }

            log.info("Exportacao em Excel acionada");
            return;
        }

        throw new RuntimeException("Nao encontrei o botao de exportar em Excel na tela do relatorio.");
    }

    private void definirValor(WebDriver driver, String id, String valor) {
        executar(driver,
                "var el = document.getElementById(arguments[0]);"
                        + "if (!el) { return; }"
                        + "el.value = arguments[1];"
                        + "if (window.jQuery) { jQuery(el).trigger('change'); }",
                id, valor);
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