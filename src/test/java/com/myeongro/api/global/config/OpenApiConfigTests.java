package com.myeongro.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenApiConfigTests {

	@Test
	void exposesMyeongroApiMetadata() {
		OpenApiConfig config = new OpenApiConfig();

		assertThat(config.myeongroOpenApi().getInfo().getTitle())
			.isEqualTo("MYEONGRO API");
	}
}
