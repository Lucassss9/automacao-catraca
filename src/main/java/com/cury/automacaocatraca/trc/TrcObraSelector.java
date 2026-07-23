package com.cury.automacaocatraca.trc;

import com.cury.automacaocatraca.config.ObraConfig;
import com.cury.automacaocatraca.config.TrcProperties;
import com.cury.automacaocatraca.domain.util.NormalizadorNome;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrcObraSelector {

    private static final Logger log = LoggerFactory.getLogger(TrcObraSelector.class);

    private static final By SELECT_OBRAS = By.id("obras");
    private static final By INPUT_OBRA_ATUAL = By.id("empreiteiras_autocomplete");

    private final TrcProperties properties;
    private final TrcAuthenticator authenticator;

    public TrcObraSelector(TrcProperties properties, TrcAuthenticator authenticator) {
        this.properties = properties;
        this.authenticator = authenticator;
    }

    public void selecionar(WebDriver driver, ObraConfig obra) {
        String idObra = descobrirIdDaObra(driver, obra.nomeTrc());

        log.info("Trocando para a obra '{}' (id {})", obra.nomeTrc(), idObra);
        driver.get(baseUrl() + "/Home2/Mudar_Obra/" + idObra);
        authenticator.fecharModais(driver);

        confirmarObraSelecionada(driver, obra.nomeTrc());
    }

    private String descobrirIdDaObra(WebDriver driver, String nomeTrc) {
        driver.get(baseUrl() + "/Home2");
        authenticator.fecharModais(driver);

        String alvo = NormalizadorNome.normalizar(nomeTrc);
        List<WebElement> opcoes = driver.findElement(SELECT_OBRAS).findElements(By.tagName("option"));

        for (WebElement opcao : opcoes) {
            String texto = NormalizadorNome.normalizar(opcao.getDomProperty("textContent"));
            if (texto.equals(alvo)) {
                return opcao.getDomAttribute("value");
            }
        }

        throw new RuntimeException(
                "Obra nao encontrada na lista do TRC: '" + nomeTrc + "'. "
                        + "Confira o nome exato em application.yml.");
    }

    private void confirmarObraSelecionada(WebDriver driver, String nomeEsperado) {
        String atual = driver.findElement(INPUT_OBRA_ATUAL).getDomProperty("value");

        if (!NormalizadorNome.normalizar(atual).equals(NormalizadorNome.normalizar(nomeEsperado))) {
            throw new RuntimeException(
                    "A troca de obra nao surtiu efeito. Esperava '" + nomeEsperado + "', obra atual: '" + atual + "'");
        }

        log.info("Obra ativa no TRC: {}", atual);
    }

    private String baseUrl() {
        String url = properties.url();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}