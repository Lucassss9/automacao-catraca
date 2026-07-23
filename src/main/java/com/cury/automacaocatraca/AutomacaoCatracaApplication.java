package com.cury.automacaocatraca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class 	AutomacaoCatracaApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutomacaoCatracaApplication.class, args);
	}
}