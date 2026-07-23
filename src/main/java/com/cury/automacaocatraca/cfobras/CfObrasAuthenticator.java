package com.cury.automacaocatraca.cfobras;

import com.cury.automacaocatraca.config.CfObrasProperties;
import org.openqa.selenium.By;
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
public class CfObrasAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(CfObrasAuthenticator.class);

    private static final By CAMPO_EMAIL = By.cssSelector("[data-automation-id='input-login-email']");
    private static final By CAMPO_SENHA = By.cssSelector("[data-automation-id='input-login-password']");
    private static final By BOTAO_ENTRAR = By.cssSelector("[data-automation-id='btn-login-submit']");
    private static final By ERRO_LOGIN = By.id("loginError");
    private static final By NOME_USUARIO = By.cssSelector("[data-automation-id='home-user-name']");
    private static final By GRID_MODULOS = By.cssSelector("[data-automation-container='home-modules']");

    private final CfObrasProperties properties;

    public CfObrasAuthenticator(CfObrasProperties properties) {
        this.properties = properties;
    }

    public void login(WebDriver driver) {
        log.info("Acessando o CF Obras em {}", properties.url());
        driver.get(properties.url());

        WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(30));

        WebElement email = espera.until(ExpectedConditions.visibilityOfElementLocated(CAMPO_EMAIL));
        email.clear();
        email.sendKeys(properties.email());

        WebElement senha = driver.findElement(CAMPO_SENHA);
        senha.clear();
        senha.sendKeys(properties.password());

        log.info("Enviando credenciais do CF Obras");
        driver.findElement(BOTAO_ENTRAR).click();

        aguardarResultadoDoLogin(driver, espera);
    }

    private void aguardarResultadoDoLogin(WebDriver driver, WebDriverWait espera) {
        try {
            espera.until(d -> homeCarregada(d) || mensagemDeErroVisivel(d));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Login no CF Obras nao concluiu no tempo esperado. URL atual: " + driver.getCurrentUrl(), e);
        }

        if (mensagemDeErroVisivel(driver)) {
            String mensagem = driver.findElement(ERRO_LOGIN).getText().trim();
            throw new RuntimeException("Login no CF Obras rejeitado: " + mensagem);
        }

        String usuario = driver.findElements(NOME_USUARIO).stream()
                .findFirst()
                .map(WebElement::getText)
                .orElse("(nao identificado)");

        log.info("Login no CF Obras concluido. Usuario: {}", usuario);
    }

    private boolean homeCarregada(WebDriver driver) {
        List<WebElement> grids = driver.findElements(GRID_MODULOS);
        return grids.stream().anyMatch(WebElement::isDisplayed);
    }

    private boolean mensagemDeErroVisivel(WebDriver driver) {
        List<WebElement> erros = driver.findElements(ERRO_LOGIN);
        return erros.stream().anyMatch(e -> e.isDisplayed() && !e.getText().isBlank());
    }
}