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

    public static String proprio(String nomeCru) {
        if (nomeCru == null || nomeCru.isBlank()) {
            return nomeCru == null ? "" : nomeCru;
        }

        StringBuilder saida = new StringBuilder(nomeCru.length());
        boolean inicioDePalavra = true;

        for (char atual : nomeCru.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(atual)) {
                saida.append(inicioDePalavra ? Character.toUpperCase(atual) : atual);
                inicioDePalavra = false;
            } else {
                saida.append(atual);
                inicioDePalavra = true;
            }
        }

        return saida.toString().replaceAll("\\s+", " ").trim();
    }
}