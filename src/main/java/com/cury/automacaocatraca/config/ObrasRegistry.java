package com.cury.automacaocatraca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "automacao")
public record ObrasRegistry(
        String downloadDir,
        List<ObraConfig> obras
) {

    public ObraConfig porCodigo(String codigo) {
        return obras.stream()
                .filter(obra -> obra.codigo().equalsIgnoreCase(codigo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Obra nao configurada: " + codigo));
    }

    public Path pastaDownload() {
        return Path.of(downloadDir);
    }
}