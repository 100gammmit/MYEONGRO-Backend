package com.myeongro.api.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI myeongroOpenApi() {
		return new OpenAPI()
			.info(new Info()
				.title("MYEONGRO API")
				.version("v1")
				.description("MYEONGRO Spring migration API"));
	}
}
