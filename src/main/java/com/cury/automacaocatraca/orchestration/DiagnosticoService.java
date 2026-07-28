package com.cury.automacaocatraca.orchestration;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DiagnosticoService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticoService.class);

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("HHmmss");
    private static final Path RAIZ = Path.of("relatorios");

    public void capturar(WebDriver driver, String codigoObra, String motivo) {
        if (driver == null) {
            return;
        }

        try {
            Path pasta = RAIZ
                    .resolve(LocalDateTime.now().format(DIA))
                    .resolve(codigoObra == null || codigoObra.isBlank() ? "SEM-OBRA" : codigoObra)
                    .resolve("falhas");

            Files.createDirectories(pasta);

            String base = LocalDateTime.now().format(MOMENTO) + "_" + limpar(motivo);

            salvarImagem(driver, pasta.resolve(base + ".png"));
            salvarHtml(driver, pasta.resolve(base + ".html"));

            log.info("Diagnostico da falha salvo em {}", pasta.toAbsolutePath());

        } catch (Exception e) {
            log.warn("Nao consegui salvar o diagnostico da falha: {}", e.getMessage());
        }
    }

    private void salvarImagem(WebDriver driver, Path destino) {
        if (!(driver instanceof TakesScreenshot camera)) {
            return;
        }

        try {
            Files.write(destino, camera.getScreenshotAs(OutputType.BYTES));
        } catch (Exception e) {
            log.warn("Print da tela falhou: {}", e.getMessage());
        }
    }

    private void salvarHtml(WebDriver driver, Path destino) {
        try {
            String html = driver.getPageSource();

            if (html == null) {
                return;
            }

            Files.writeString(destino,
                    "<!-- url: " + driver.getCurrentUrl() + " -->\n" + html,
                    StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.warn("HTML da tela falhou: {}", e.getMessage());
        }
    }

    private String limpar(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return "falha";
        }

        String limpo = motivo.replaceAll("[^A-Za-z0-9]+", "-").toLowerCase();

        return limpo.length() > 40 ? limpo.substring(0, 40) : limpo;
    }
}