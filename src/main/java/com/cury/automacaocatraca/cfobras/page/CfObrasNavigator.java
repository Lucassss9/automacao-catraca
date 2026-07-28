package com.cury.automacaocatraca.cfobras.page;

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

@Component
public class CfObrasNavigator {

    private static final Logger log = LoggerFactory.getLogger(CfObrasNavigator.class);

    private static final By CARD_SERVICOS = By.cssSelector("[data-automation-id='home-module-servicos']");
    private static final By IFRAME_SERVICOS = By.cssSelector("[data-automation-iframe='servicos']");
    private static final By SUBMENU_SERVICOS = By.id("submenu-servicos");
    private static final int TENTATIVAS = 3;

    public void abrirModuloServicos(WebDriver driver) {
        driver.switchTo().defaultContent();

        WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(30));
        WebElement card = espera.until(ExpectedConditions.elementToBeClickable(CARD_SERVICOS));
        card.click();

        entrarNoIframe(driver);
        log.info("Modulo Servicos aberto");
    }

    public void entrarNoIframe(WebDriver driver) {
        driver.switchTo().defaultContent();

        WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(30));
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