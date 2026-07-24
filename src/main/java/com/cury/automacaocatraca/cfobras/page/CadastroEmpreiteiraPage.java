package com.cury.automacaocatraca.cfobras;

import com.cury.automacaocatraca.domain.dto.DadosCadastroEmpreiteira;
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
import java.util.List;

@Component
public class CadastroEmpreiteiraPage {

    private static final Logger log = LoggerFactory.getLogger(CadastroEmpreiteiraPage.class);

    private static final By RAZAO_SOCIAL = By.id("inpRazaoSocialEmpr");
    private static final By NOME_FANTASIA = By.id("inpNomeFantasiaEmpr");
    private static final By CNPJ = By.id("inpCNPJEmpr");
    private static final By TELEFONE = By.id("inpTelefoneEmpr");
    private static final By WHATSAPP = By.id("inpWhatsAppEmpr");
    private static final By EMAIL = By.id("inpEmailEmpr");
    private static final By COR = By.id("inpCorEmpr");
    private static final By VALOR_UNITARIO = By.id("inpValorUnitarioEmpr");
    private static final By BOTAO_SALVAR = By.id("btnSalvarEmpreiteiro");
    private static final By STATUS = By.id("statusEmpreiteiros");

    private final CfObrasNavigator navigator;

    public CadastroEmpreiteiraPage(CfObrasNavigator navigator) {
        this.navigator = navigator;
    }

    public void cadastrarEmpreiteira(WebDriver driver,
                                     DadosCadastroEmpreiteira dados,
                                     String idObraCfObras) {
        navigator.abrirAba(driver, "empreiteiros");

        preencher(driver, RAZAO_SOCIAL, dados.razaoSocial());
        preencher(driver, NOME_FANTASIA, dados.nomeFantasia());
        preencher(driver, CNPJ, dados.cnpj());
        preencher(driver, TELEFONE, dados.telefone());
        preencher(driver, WHATSAPP, dados.whatsapp());
        preencher(driver, EMAIL, dados.email());
        preencher(driver, VALOR_UNITARIO, "0,00");

        definirCor(driver, dados.corIdentificacao());
        vincularObra(driver, idObraCfObras);

        driver.findElement(BOTAO_SALVAR).click();

        confirmarSalvamento(driver, dados.razaoSocial());
    }

    private void preencher(WebDriver driver, By campo, String valor) {
        WebElement elemento = driver.findElement(campo);
        elemento.clear();
        if (valor != null && !valor.isBlank()) {
            elemento.sendKeys(valor);
        }
    }

    private void definirCor(WebDriver driver, String hex) {
        WebElement input = driver.findElement(COR);
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];"
                        + "arguments[0].dispatchEvent(new Event('input', {bubbles:true}));"
                        + "arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
                input, hex);
    }

    private void vincularObra(WebDriver driver, String idObraCfObras) {
        By checkbox = By.cssSelector("#obrasVinculadasEmpr input[value='" + idObraCfObras + "']");
        List<WebElement> encontrados = driver.findElements(checkbox);

        if (encontrados.isEmpty()) {
            throw new RuntimeException("Obra '" + idObraCfObras + "' nao encontrada na lista de obras vinculadas.");
        }

        WebElement item = encontrados.get(0);
        if (!item.isSelected()) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", item);
        }
    }

    private void confirmarSalvamento(WebDriver driver, String razaoSocial) {
        WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(20));

        try {
            espera.until(ExpectedConditions.visibilityOfElementLocated(STATUS));
        } catch (Exception e) {
            log.warn("Nao consegui confirmar visualmente o cadastro de '{}'", razaoSocial);
            return;
        }

        String mensagem = driver.findElement(STATUS).getText().trim();
        log.info("Cadastro de '{}': {}", razaoSocial, mensagem);
    }
}