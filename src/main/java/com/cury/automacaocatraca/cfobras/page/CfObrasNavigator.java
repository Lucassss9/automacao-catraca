package com.cury.automacaocatraca.cfobras;

import org.openqa.selenium.By;
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
        By botao = By.cssSelector("#submenu-servicos .sub-btn[data-secao='" + secao + "']");

        WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(20));
        WebElement aba = espera.until(ExpectedConditions.elementToBeClickable(botao));
        aba.click();

        espera.until(ExpectedConditions.visibilityOfElementLocated(By.id("secao-" + secao)));
        log.info("Aba '{}' aberta", secao);
    }
}