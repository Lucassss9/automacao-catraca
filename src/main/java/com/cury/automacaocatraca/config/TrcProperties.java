package com.cury.automacaocatraca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trc")
public record TrcProperties(
        String url,
        String email,
        String password
) {}