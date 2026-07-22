package com.cury.automacaocatraca.domain.util;

import java.text.Normalizer;
import java.util.Locale;

public final class NormalizadorNome {

    private NormalizadorNome() {
    }

    public static String normalizar(String nomeCru) {
        if (nomeCru == null) {
            return "";
        }
        String semAcentos = Normalizer.normalize(nomeCru, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcentos
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}