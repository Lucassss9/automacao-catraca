package com.cury.automacaocatraca.domain.dto;

import java.math.BigDecimal;

public record DadosCadastroEmpreiteira(
        String razaoSocial,
        String nomeFantasia,
        String cnpj,
        String telefone,
        String whatsapp,
        String email,
        String corIdentificacao,
        BigDecimal valorUnitario
) {}