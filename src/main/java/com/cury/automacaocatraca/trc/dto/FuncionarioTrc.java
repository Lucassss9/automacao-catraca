package com.cury.automacaocatraca.trc.dto;

import java.time.LocalDate;

public record FuncionarioTrc(
        String nome,
        String cpf,
        String rg,
        LocalDate dataNascimento,
        String funcao,
        String telefone,
        String email,
        String empreiteiraNome,
        LocalDate entradaNaObra
) {}