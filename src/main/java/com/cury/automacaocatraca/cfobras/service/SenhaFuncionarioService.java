package com.cury.automacaocatraca.cfobras.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SenhaFuncionarioService {

    private static final Logger log = LoggerFactory.getLogger(SenhaFuncionarioService.class);

    private static final int MINIMO = 6;
    private static final String COMPLEMENTO = "cfobras";

    private final String estrategia;
    private final String valorFixo;

    public SenhaFuncionarioService(
            @Value("${cfobras.senha-funcionario.estrategia:cpf}") String estrategia,
            @Value("${cfobras.senha-funcionario.valor:}") String valorFixo) {
        this.estrategia = estrategia == null ? "cpf" : estrategia.trim().toLowerCase();
        this.valorFixo = valorFixo == null ? "" : valorFixo.trim();
    }

    public String gerar(String cpf) {
        if ("fixa".equals(estrategia)) {
            if (valorFixo.length() >= MINIMO) {
                return valorFixo;
            }
            log.warn("cfobras.senha-funcionario.valor tem menos de {} caracteres — usando o CPF", MINIMO);
        }

        String digitos = cpf == null ? "" : cpf.replaceAll("\\D", "");

        if (digitos.length() >= MINIMO) {
            return digitos;
        }

        log.warn("CPF invalido para gerar senha ('{}') — usando complemento padrao", cpf);
        return digitos + COMPLEMENTO;
    }

    public String descricao() {
        return "fixa".equals(estrategia) ? "senha fixa da configuracao" : "CPF sem pontuacao";
    }
}