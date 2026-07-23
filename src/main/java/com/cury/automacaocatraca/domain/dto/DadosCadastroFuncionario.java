package com.cury.automacaocatraca.domain.dto;

import com.cury.automacaocatraca.domain.enums.NivelAcesso;

import java.time.LocalDate;

public record DadosCadastroFuncionario(
        String nomeCompleto,
        String cpf,
        String rg,
        LocalDate dataNascimento,
        String telefone,
        String email,
        String funcao,
        LocalDate dataAdmissao,
        NivelAcesso nivelAcesso
) {}