package com.cury.automacaocatraca.trc;

import com.cury.automacaocatraca.config.TrcProperties;
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
public class TrcAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(TrcAuthenticator.class);

    private static final By CAMPO_USUARIO = By.id("usuario");
    private static final By CAMPO_SENHA = By.id("senha");
    private static final By BOTAO_ENTRAR = By.id("Entrar");
    private static final By FORM_TROCA_SENHA = By.cssSelector("form[tipo='senha']");
    private static final By LINK_SAIR = By.cssSelector("a[href*='logout']");

    private static final String SELETOR_MODAL_JS =
            "#notificacao, .modal.show, .modal[style*='display: block'], .modal[style*='display:block'], "
                    + ".ui-dialog, .reveal-modal[style*='display: block'], .modal-backdrop, .ui-widget-overlay";

    private static final String SELETOR_BOTOES_JS =
            "#notificacao .modal-footer button, #notificacao button, "
                    + ".modal .modal-footer button, .modal .modal-footer input[type=button], "
                    + ".modal .modal-footer input[type=submit], .ui-dialog button, .reveal-modal button";

    private final TrcProperties properties;

    public TrcAuthenticator(TrcProperties properties) {
        this.properties = properties;
    }

    public void login(WebDriver driver) {
        log.info("Acessando o TRC em {}", properties.url());
        driver.get(properties.url());

        WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(20));

        fecharModais(driver);

        WebElement usuario = espera.until(ExpectedConditions.visibilityOfElementLocated(CAMPO_USUARIO));
        usuario.clear();
        usuario.sendKeys(properties.email());

        WebElement senha = driver.findElement(CAMPO_SENHA);
        senha.clear();
        senha.sendKeys(properties.password());

        log.info("Enviando credenciais do TRC");
        driver.findElement(BOTAO_ENTRAR).click();

        aguardarEFecharModais(driver, Duration.ofSeconds(15));

        aguardarResultadoDoLogin(driver);

        aguardarEFecharModais(driver, Duration.ofSeconds(8));
    }

    public void fecharModais(WebDriver driver) {
        aguardarEFecharModais(driver, Duration.ofSeconds(4));
    }

    public void aguardarEFecharModais(WebDriver driver, Duration tempoDeEspera) {
        long limite = System.currentTimeMillis() + tempoDeEspera.toMillis();
        int fechados = 0;

        while (System.currentTimeMillis() < limite) {
            if (!existeModalVisivel(driver)) {
                esperar(400);
                continue;
            }

            String rotulo = clicarNoBotaoViaJavaScript(driver);

            if (rotulo != null) {
                fechados++;
                log.info("Modal fechado pelo botao '{}'", rotulo);
                esperar(2000);
            } else {
                log.warn("Modal visivel sem botao clicavel — removendo via JavaScript");
                removerModalViaJavaScript(driver);
                esperar(800);
            }
        }

        if (fechados > 0) {
            log.info("Total de modais fechados: {}", fechados);
        }
    }

    private String clicarNoBotaoViaJavaScript(WebDriver driver) {
        try {
            Object resultado = ((JavascriptExecutor) driver).executeScript(
                    "var botoes = document.querySelectorAll(arguments[0]);"
                            + "for (var i = 0; i < botoes.length; i++) {"
                            + "  var b = botoes[i];"
                            + "  if (b.offsetParent === null && b.getClientRects().length === 0) { continue; }"
                            + "  var texto = (b.innerText || b.value || '').trim();"
                            + "  b.click();"
                            + "  return texto;"
                            + "}"
                            + "return null;",
                    SELETOR_BOTOES_JS);

            return resultado == null ? null : resultado.toString();
        } catch (Exception e) {
            log.debug("Falha ao clicar no botao do modal: {}", e.getMessage());
            return null;
        }
    }

    private boolean existeModalVisivel(WebDriver driver) {
        try {
            Object resultado = ((JavascriptExecutor) driver).executeScript(
                    "var els = document.querySelectorAll(arguments[0]);"
                            + "for (var i = 0; i < els.length; i++) {"
                            + "  var e = els[i];"
                            + "  if (e.offsetParent !== null || e.getClientRects().length > 0) { return true; }"
                            + "}"
                            + "return false;",
                    SELETOR_MODAL_JS);

            return Boolean.TRUE.equals(resultado);
        } catch (Exception e) {
            return false;
        }
    }

    private void removerModalViaJavaScript(WebDriver driver) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "document.querySelectorAll('#notificacao, .modal, .modal-backdrop, .ui-widget-overlay, .ui-dialog, .reveal-modal')"
                            + ".forEach(function(el){ el.remove(); });"
                            + "document.body.classList.remove('modal-open');"
                            + "document.body.style.overflow = 'auto';"
                            + "document.body.style.pointerEvents = 'auto';"
            );
        } catch (Exception e) {
            log.debug("Falha ao remover modal via JavaScript: {}", e.getMessage());
        }
    }

    private void aguardarResultadoDoLogin(WebDriver driver) {
        WebDriverWait espera = new WebDriverWait(driver, Duration.ofSeconds(30));

        try {
            espera.until(d -> !d.findElements(LINK_SAIR).isEmpty()
                    || formDeTrocaDeSenhaVisivel(d)
                    || !d.getCurrentUrl().toLowerCase().contains("/login"));
        } catch (Exception e) {
            throw new RuntimeException(
                    "Login no TRC nao concluiu no tempo esperado. URL atual: " + driver.getCurrentUrl(), e);
        }

        if (formDeTrocaDeSenhaVisivel(driver)) {
            throw new RuntimeException(
                    "O TRC esta exigindo troca de senha. Troque manualmente e atualize o .env antes de rodar o robo.");
        }

        if (driver.getCurrentUrl().toLowerCase().contains("/login")) {
            throw new RuntimeException(
                    "Login no TRC falhou — continuo na tela de login. Confira usuario e senha no .env.");
        }

        log.info("Login no TRC concluido. URL: {}", driver.getCurrentUrl());
    }

    private boolean formDeTrocaDeSenhaVisivel(WebDriver driver) {
        List<WebElement> forms = driver.findElements(FORM_TROCA_SENHA);
        return forms.stream().anyMatch(WebElement::isDisplayed);
    }

    private void esperar(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}