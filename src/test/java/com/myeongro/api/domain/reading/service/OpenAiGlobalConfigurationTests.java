package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class OpenAiGlobalConfigurationTests {

	@Test
	void pinsThePublishedGlobalApiEndpoint() throws IOException {
		String yaml = Files.readString(
			Path.of("src", "main", "resources", "application.yaml")
		).replace("\r\n", "\n");

		assertThat(yaml)
			.contains("base-url: https://api.openai.com")
			.doesNotContain("us.api.openai.com", "eu.api.openai.com", "kr.api.openai.com");
	}
}
