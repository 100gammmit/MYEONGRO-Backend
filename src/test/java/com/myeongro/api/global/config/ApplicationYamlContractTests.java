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

		assertThat(applicationYaml).contains("api-key: ${openai.api-key:}");
		assertThat(applicationYaml).contains("model: ${openai.free-model:");
	}

	@Test
	void redisSessionStoreIsTheDefaultRuntimeSessionStore() throws Exception {
		String applicationYaml = Files.readString(
			Path.of("src/main/resources/application.yaml"),
			StandardCharsets.UTF_8
		);

		assertThat(applicationYaml).contains("repository-type: default");
		assertThat(applicationYaml).contains("namespace: myeongro:session");
		assertThat(applicationYaml).contains("flush-mode: on_save");
		assertThat(applicationYaml).contains("timeout: 30m");
		assertThat(applicationYaml).contains("host: ${redis.host:localhost}");
		assertThat(applicationYaml).contains("port: ${redis.port:6379}");
	}
}
