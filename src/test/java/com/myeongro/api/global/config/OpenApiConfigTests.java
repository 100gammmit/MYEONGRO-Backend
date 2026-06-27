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

	@Test
	void doesNotExposeLegacyBearerAuthScheme() {
		OpenApiConfig config = new OpenApiConfig();

		assertThat(config.myeongroOpenApi().getComponents())
			.satisfiesAnyOf(
				components -> assertThat(components).isNull(),
				components -> assertThat(components.getSecuritySchemes()).isNullOrEmpty()
			);
	}

	@Test
	void doesNotRequireBearerAuthGlobally() {
		OpenApiConfig config = new OpenApiConfig();

		assertThat(config.myeongroOpenApi().getSecurity())
			.isNullOrEmpty();
	}
}
