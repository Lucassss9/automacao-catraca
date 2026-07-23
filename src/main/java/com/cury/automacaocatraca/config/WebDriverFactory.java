package com.cury.automacaocatraca.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class WebDriverFactory {

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
        opcoes.addArguments("--start-maximized");
        opcoes.addArguments("--remote-allow-origins=*");
        opcoes.addArguments("--disable-notifications");
        opcoes.addArguments("--disable-save-password-bubble");
        opcoes.addArguments("--disable-features=PasswordLeakDetection,PasswordLeakToggleMove,AutofillServerCommunication");

        WebDriver driver = new ChromeDriver(opcoes);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

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