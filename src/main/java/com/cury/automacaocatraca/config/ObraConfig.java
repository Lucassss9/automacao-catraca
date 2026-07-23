package com.cury.automacaocatraca.config;

import java.util.List;

public record ObraConfig(
        String codigo,
        String nomeTrc,
        List<String> nomesCfObras
) {}