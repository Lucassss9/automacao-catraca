package com.cury.automacaocatraca.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class WebDriverFactory {

    private static final Logger log = LoggerFactory.getLogger(WebDriverFactory.class);

    private final boolean headless;
    private final int esperaImplicitaSegundos;

    public WebDriverFactory(
            @Value("${automacao.navegador.headless:false}") boolean headless,
            @Value("${automacao.navegador.espera-implicita-segundos:3}") int esperaImplicitaSegundos) {
        this.headless = headless;
        this.esperaImplicitaSegundos = Math.max(0, esperaImplicitaSegundos);
    }

    public WebDriver criar(Path pastaDownload) {
        criarPastaSeNecessario(pastaDownload);

        Map<String, Object> preferencias = new HashMap<>();
        preferencias.put("download.default_directory", pastaDownload.toAbsolutePath().toString());
        preferencias.put("download.prompt_for_download", false);
        preferencias.put("download.directory_upgrade", true);
        preferencias.put("safebrowsing.enabled", true);
        preferencias.put("profile.default_content_settings.popups", 0);
        preferencias.put("credentials_enable_service", false);
        preferencias.put("profile.password_manager_enabled", false);
        preferencias.put("profile.password_manager_leak_detection", false);

        ChromeOptions opcoes = new ChromeOptions();
        opcoes.setExperimentalOption("prefs", preferencias);
        opcoes.addArguments("--remote-allow-origins=*");
        opcoes.addArguments("--disable-notifications");
        opcoes.addArguments("--disable-save-password-bubble");
        opcoes.addArguments("--disable-features=PasswordLeakDetection,PasswordLeakToggleMove,AutofillServerCommunication");

        if (headless) {
            opcoes.addArguments("--headless=new");
            opcoes.addArguments("--window-size=1920,1080");
            opcoes.addArguments("--disable-gpu");
            opcoes.addArguments("--no-sandbox");
            opcoes.addArguments("--disable-dev-shm-usage");
        } else {
            opcoes.addArguments("--start-maximized");
        }

        WebDriver driver = new ChromeDriver(opcoes);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(esperaImplicitaSegundos));

        log.info("Navegador aberto (headless={}, espera implicita={}s)",
                headless, esperaImplicitaSegundos);

        return driver;
    }

    private void criarPastaSeNecessario(Path pasta) {
        try {
            Files.createDirectories(pasta);
        } catch (IOException e) {
            throw new RuntimeException("Nao consegui criar a pasta de download: " + pasta, e);
        }
    }
}