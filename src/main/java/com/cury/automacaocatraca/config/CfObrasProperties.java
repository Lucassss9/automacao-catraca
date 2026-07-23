package com.cury.automacaocatraca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cfobras")
public record CfObrasProperties(
        String url,
        String email,
        String password
) {}