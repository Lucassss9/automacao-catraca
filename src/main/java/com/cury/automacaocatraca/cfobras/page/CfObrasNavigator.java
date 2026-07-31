package com.cury.automacaocatraca.cfobras.page;

import com.cury.automacaocatraca.config.CfObrasProperties;
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
public class CfObrasNavigator {

    private static final Logger log = LoggerFactory.getLogger(CfObrasNavigator.class);

    private static final By CARD_SERVICOS = By.cssSelector("[data-automation-id='home-module-servicos']");
    private static final By IFRAME_SERVICOS = By.cssSelector("[data-automation-iframe='servicos']");
    private static final By SUBMENU_SERVICOS = By.id("submenu-servicos");
    private static final By GRID_MODULOS = By.cssSelector("[data-automation-container='home-modules']");
    private static final By CAMPO_EMAIL = By.cssSelector("[data-automation-id='input-login-email']");
    private static final int TENTATIVAS = 3;
    private static final int ESPERA_CURTA_SEGUNDOS = 8;
    private static final int ESPERA_LONGA_SEGUNDOS = 30;

    private final CfObrasProperties properties;

    public CfObrasNavigator(CfObrasProperties properties) {
        this.properties = properties;
    }

    public void abrirModuloServicos(WebDriver driver) {
        driver.switchTo().defaultContent();

        if (iframeVisivel(driver)) {
            entrarNoIframe(driver);
            log.info("Modulo Servicos ja estava aberto — reaproveitando o iframe");
            return;
        }

        if (clicarNoCard(driver, ESPERA_CURTA_SEGUNDOS)) {
            entrarNoIframe(driver);
            log.info("Modulo Servicos aberto");
            return;
        }

        log.warn("O card Servicos nao ficou clicavel — recarregando a home do CF Obras");
        voltarParaHome(driver);

        if (!clicarNoCard(driver, ESPERA_LONGA_SEGUNDOS)) {
            throw new IllegalStateException(
                    "O card Servicos nao ficou clicavel nem depois de recarregar a home. URL atual: "
                            + driver.getCurrentUrl());
        }

        entrarNoIframe(driver);
        log.info("Modulo Servicos aberto depois de recarregar a home");
    }

    public void entrarNoIframe(WebDriver driver) {
        driver.switchTo().defaultContent();

        WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(ESPERA_LONGA_SEGUNDOS));
        espera.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(IFRAME_SERVICOS));
        espera.until(ExpectedConditions.presenceOfElementLocated(SUBMENU_SERVICOS));
    }

    public void sairDoIframe(WebDriver driver) {
        driver.switchTo().defaultContent();
    }

    public void abrirAba(WebDriver driver, String secao) {
        Exception ultimoErro = null;

        for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
            try {
                if (tentativa > 1) {
                    log.info("Reentrando no iframe antes de tentar a aba '{}' de novo", secao);
                    entrarNoIframe(driver);
                }

                clicar(driver, secao);
                esperarSecao(driver, secao);

                log.info("Aba '{}' aberta", secao);
                return;

            } catch (Exception e) {
                ultimoErro = e;
                log.warn("Aba '{}' nao abriu (tentativa {}/{})", secao, tentativa, TENTATIVAS);
                dormir(1500);
            }
        }

        throw new IllegalStateException(
                "Nao consegui abrir a aba '" + secao + "' apos " + TENTATIVAS + " tentativas",
                ultimoErro);
    }

    private boolean iframeVisivel(WebDriver driver) {
        try {
            List<WebElement> iframes = driver.findElements(IFRAME_SERVICOS);
            return iframes.stream().anyMatch(WebElement::isDisplayed);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean clicarNoCard(WebDriver driver, int segundos) {
        try {
            WebElement card = new WebDriverWait(driver, Duration.ofSeconds(segundos))
                    .until(ExpectedConditions.elementToBeClickable(CARD_SERVICOS));
            card.click();
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private void voltarParaHome(WebDriver driver) {
        driver.switchTo().defaultContent();
        driver.get(properties.url());

        try {
            new WebDriverWait(driver, Duration.ofSeconds(ESPERA_LONGA_SEGUNDOS))
                    .until(d -> visivel(d, GRID_MODULOS) || visivel(d, CAMPO_EMAIL));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "A home do CF Obras nao carregou depois do recarregamento. URL atual: "
                            + driver.getCurrentUrl(), e);
        }

        if (visivel(driver, CAMPO_EMAIL)) {
            throw new IllegalStateException(
                    "O CF Obras voltou para a tela de login ao recarregar a home — a sessao caiu");
        }
    }

    private boolean visivel(WebDriver driver, By seletor) {
        try {
            return driver.findElements(seletor).stream().anyMatch(WebElement::isDisplayed);
        } catch (Exception e) {
            return false;
        }
    }

    private void clicar(WebDriver driver, String secao) {
        By botao = By.cssSelector("#submenu-servicos .sub-btn[data-secao='" + secao + "']");

        WebElement aba = new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(ExpectedConditions.elementToBeClickable(botao));

        try {
            aba.click();
        } catch (Exception e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", aba);
        }
    }

    private void esperarSecao(WebDriver driver, String secao) {
        new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(ExpectedConditions.visibilityOfElementLocated(By.id("secao-" + secao)));
    }

    private void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}