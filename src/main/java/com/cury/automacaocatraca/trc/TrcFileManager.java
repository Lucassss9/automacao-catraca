package com.cury.automacaocatraca.trc;

import com.cury.automacaocatraca.config.ObraConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class TrcFileManager {

    private static final Logger log = LoggerFactory.getLogger(TrcFileManager.class);
    private static final DateTimeFormatter FORMATO_ARQUIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public Set<String> listarArquivos(Path pasta) {
        try (Stream<Path> arquivos = Files.list(pasta)) {
            Set<String> nomes = new HashSet<>();
            arquivos.forEach(p -> nomes.add(p.getFileName().toString()));
            return nomes;
        } catch (IOException e) {
            return new HashSet<>();
        }
    }

    public Path aguardarNovoArquivo(Path pasta, Set<String> antes, Duration timeout) {
        long limite = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < limite) {
            Path candidato = procurarNovoXlsx(pasta, antes);

            if (candidato != null && downloadTerminou(pasta, candidato)) {
                log.info("Download concluido: {}", candidato.getFileName());
                return candidato;
            }

            dormir(500);
        }

        throw new RuntimeException(
                "O arquivo nao apareceu em " + pasta.toAbsolutePath() + " dentro de " + timeout.toSeconds() + "s");
    }

    public Path renomear(Path arquivo, ObraConfig obra, LocalDate data) {
        String novoNome = "Frequencia_" + obra.codigo() + "_" + data.format(FORMATO_ARQUIVO) + ".xlsx";
        Path destino = arquivo.getParent().resolve(novoNome);

        try {
            Files.move(arquivo, destino, StandardCopyOption.REPLACE_EXISTING);
            log.info("Arquivo renomeado para {}", novoNome);
            return destino;
        } catch (IOException e) {
            throw new RuntimeException("Nao consegui renomear o arquivo baixado para " + novoNome, e);
        }
    }

    private Path procurarNovoXlsx(Path pasta, Set<String> antes) {
        try (Stream<Path> arquivos = Files.list(pasta)) {
            return arquivos
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".xlsx"))
                    .filter(p -> !antes.contains(p.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private boolean downloadTerminou(Path pasta, Path arquivo) {
        if (existeArquivoParcial(pasta)) {
            return false;
        }

        try {
            long tamanho1 = Files.size(arquivo);
            dormir(700);
            long tamanho2 = Files.size(arquivo);
            return tamanho1 > 0 && tamanho1 == tamanho2;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean existeArquivoParcial(Path pasta) {
        try (Stream<Path> arquivos = Files.list(pasta)) {
            return arquivos.anyMatch(p -> p.getFileName().toString().endsWith(".crdownload"));
        } catch (IOException e) {
            return false;
        }
    }

    private void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}