package com.cury.automacaocatraca.cfobras.dto;

public record EmpreiteiraMapeada(
        String nomeArquivo,
        String nomeNormalizado,
        String idEmpreiteiroSelecionado,
        String nomeEmpreiteiroSelecionado,
        String situacao
) {
    public boolean mapeada() {
        return idEmpreiteiroSelecionado != null
                && !idEmpreiteiroSelecionado.isBlank()
                && !"null".equals(idEmpreiteiroSelecionado);
    }
}