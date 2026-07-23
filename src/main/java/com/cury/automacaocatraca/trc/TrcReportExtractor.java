package com.cury.automacaocatraca.trc;

import com.cury.automacaocatraca.config.ObraConfig;
import com.cury.automacaocatraca.config.TrcProperties;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public TrcReportExtractor(TrcProperties properties,
                              TrcAuthenticator authenticator,
                              TrcFileManager fileManager) {
        this.properties = properties;
        this.authenticator = authenticator;
        this.fileManager = fileManager;
    }

    public Path extrairRelatorioDoDia(WebDriver driver, ObraConfig obra, Path pastaDownload) {
        LocalDate hoje = LocalDate.now();

        abrirTelaDoRelatorio(driver);
        preencherDatas(driver, hoje);
        selecionarObra(driver, obra);
        marcarSelecionarTodos(driver, "selAllEm", "empreiteiras");
        marcarSelecionarTodos(driver, "selAllFn", "funcionarios");

        Set<String> antes = fileManager.listarArquivos(pastaDownload);

        gerarRelatorio(driver);
        clicarExportar(driver);

        Path baixado = fileManager.aguardarNovoArquivo(pastaDownload, antes, Duration.ofSeconds(120));
        return fileManager.renomear(baixado, obra, hoje);
    }

    private void abrirTelaDoRelatorio(WebDriver driver) {
        log.info("Abrindo a tela de relatorios do TRC");
        driver.get(baseUrl() + "/Relatorio_Listar");
        authenticator.fecharModais(driver);

        WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(20));
        WebElement item = espera.until(ExpectedConditions.elementToBeClickable(ITEM_RELATORIO));
        item.click();

        espera.until(ExpectedConditions.visibilityOfElementLocated(CAMPO_DATA_INICIAL));
        esperar(1500);
        log.info("Relatorio 03.1 - Frequencia - Catraca selecionado");
    }

    private void preencherDatas(WebDriver driver, LocalDate data) {
        String texto = data.format(FORMATO_TELA);
        definirValor(driver, "dt_inicial", texto);
        definirValor(driver, "dt_final", texto);
        log.info("Periodo do relatorio: {} a {}", texto, texto);
    }

    private void selecionarObra(WebDriver driver, ObraConfig obra) {
        String alvo = NormalizadorNome.normalizar(obra.nomeTrc());

        Boolean encontrou = (Boolean) executar(driver,
                "var alvo = arguments[0];"
                        + "var sel = document.getElementById('obra_sel');"
                        + "if (!sel) { return false; }"
                        + "var achou = false;"
                        + "for (var i = 0; i < sel.options.length; i++) {"
                        + "  var texto = sel.options[i].textContent"
                        + "      .normalize('NFD').replace(/[\\u0300-\\u036f]/g, '')"
                        + "      .toUpperCase().replace(/\\s+/g, ' ').trim();"
                        + "  var bate = (texto === alvo);"
                        + "  sel.options[i].selected = bate;"
                        + "  if (bate) { achou = true; }"
                        + "}"
                        + "if (window.jQuery) { jQuery(sel).trigger('chosen:updated').trigger('change'); }"
                        + "return achou;",
                alvo);

        if (!Boolean.TRUE.equals(encontrou)) {
            throw new RuntimeException(
                    "Obra '" + obra.nomeTrc() + "' nao existe na lista do relatorio 03.1 do TRC.");
        }

        log.info("Obra selecionada no relatorio: {}", obra.nomeTrc());
        esperar(500);
    }

    private void marcarSelecionarTodos(WebDriver driver, String idCheckbox, String descricao) {
        List<WebElement> checkboxes = driver.findElements(By.id(idCheckbox));

        if (checkboxes.isEmpty()) {
            throw new RuntimeException("Checkbox '" + idCheckbox + "' nao encontrado na tela do relatorio.");
        }

        WebElement checkbox = checkboxes.get(0);

        if (checkbox.isSelected()) {
            log.info("Checkbox de todas as {} ja estava marcado", descricao);
            return;
        }

        try {
            checkbox.click();
        } catch (Exception e) {
            executar(driver, "arguments[0].click();", checkbox);
        }

        esperar(1500);
        log.info("Marcado 'Selecionar todas as {}'", descricao);
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

        new WebDriverWait(driver, Duration.ofSeconds(180))
                .until(d -> d.findElements(BOTAO_EXPORTAR).stream().anyMatch(WebElement::isDisplayed));

        log.info("Relatorio gerado");
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