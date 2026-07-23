package com.cury.automacaocatraca.excel;

import lombok.AllArgsConstructor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor
public class RelatorioFrequencia {

    private final String nomeObra;
    private final Map<String, Set<String>> dados;


    public String nomeObra() {
        return nomeObra;
    }

    public Set<String> empreiteiras() {
        return Collections.unmodifiableSet(dados.keySet());
    }

    public Set<String> funcionariosDa(String empreiteira) {
        return Collections.unmodifiableSet(dados.getOrDefault(empreiteira, Set.of()));
    }
}