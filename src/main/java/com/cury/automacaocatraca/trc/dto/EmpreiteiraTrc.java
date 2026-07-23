package com.cury.automacaocatraca.trc.dto;

public record EmpreiteiraTrc(
        String razaoSocial,
        String cnpj,
        String nomeFantasia,
        String contatoNome,
        String contatoTelefone,
        String contatoCelular,
        String contatoEmail
) {}