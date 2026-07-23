package com.cury.automacaocatraca.cfobras;

import java.util.Collections;
import java.util.List;

public record PlanoConciliacao(
        String nomeObra,
        List<String> empreiteirasExistentes,
        List<String> empreiteirasParaCadastrar,
        List<PendenciaFuncionario> funcionariosParaCadastrar
) {

    public PlanoConciliacao {
        empreiteirasExistentes = List.copyOf(empreiteirasExistentes);
        empreiteirasParaCadastrar = List.copyOf(empreiteirasParaCadastrar);
        funcionariosParaCadastrar = List.copyOf(funcionariosParaCadastrar);
    }

    public boolean temPendencias() {
        return !empreiteirasParaCadastrar.isEmpty() || !funcionariosParaCadastrar.isEmpty();
    }

    public int totalPendencias() {
        return empreiteirasParaCadastrar.size() + funcionariosParaCadastrar.size();
    }

    public List<String> todasEmpreiteiras() {
        List<String> todas = new java.util.ArrayList<>(empreiteirasExistentes);
        todas.addAll(empreiteirasParaCadastrar);
        Collections.sort(todas);
        return List.copyOf(todas);
    }
}