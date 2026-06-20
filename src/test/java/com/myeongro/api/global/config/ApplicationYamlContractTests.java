package com.myeongro.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ApplicationYamlContractTests {

	@Test
	void defaultDemoModeDoesNotRequireOpenAiApiKeyPlaceholder() throws Exception {
		String applicationYaml = Files.readString(
			Path.of("src/main/resources/application.yaml"),
			StandardCharsets.UTF_8
		);

		assertThat(applicationYaml).contains("generator: demo");
		assertThat(applicationYaml).doesNotContain("api-key: ${openai.api-key}");
	}
}
