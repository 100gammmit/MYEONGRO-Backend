package com.myeongro.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

class ApplicationYamlContractTests {

	@Test
	void tarotOpenAiConfigurationKeepsModelAndApiKeyExternalized() throws Exception {
		String applicationYaml = Files.readString(
			Path.of("src/main/resources/application.yaml"),
			StandardCharsets.UTF_8
		);

		assertThat(applicationYaml).contains("api-key: ${openai.api-key:}");
		assertThat(applicationYaml).contains("model: ${openai.model:");
	}

	@Test
	void readingPromptConfigurationReferencesReadableClasspathResources() throws Exception {
		List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
			"applicationYaml",
			new FileSystemResource("src/main/resources/application.yaml")
		);

		assertThat(propertySources).hasSize(1);
		PropertySource<?> properties = propertySources.getFirst();
		List<String> promptKeys = List.of(
			"app.reading.prompts.tarot.common",
			"app.reading.prompts.tarot.cards",
			"app.reading.prompts.tarot.spreads.daily-one-card",
			"app.reading.prompts.tarot.spreads.mind-three-card",
			"app.reading.prompts.tarot.spreads.relationship-three-card",
			"app.reading.prompts.tarot.spreads.choice-five-card",
			"app.reading.prompts.saju.common",
			"app.reading.prompts.saju.interpretation",
			"app.reading.prompts.saju.reports.birth-annual-question"
		);

		assertThat(promptKeys).allSatisfy(key -> {
			Object configuredValue = properties.getProperty(key);
			assertThat(configuredValue).as(key).isInstanceOf(String.class);
			String location = (String) configuredValue;
			assertThat(location).as(key).startsWith("classpath:");

			ClassPathResource resource = new ClassPathResource(
				location.substring("classpath:".length())
			);
			assertThat(resource.exists()).as(key + " exists").isTrue();
			assertThat(resource.isReadable()).as(key + " is readable").isTrue();
		});
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
	}

	@Test
	void healthEndpointIsExposedForDeploymentVerification() throws Exception {
		String applicationYaml = Files.readString(
			Path.of("src/main/resources/application.yaml"),
			StandardCharsets.UTF_8
		);

		assertThat(applicationYaml).contains("include: health");
		assertThat(applicationYaml).contains("enabled: true");
		assertThat(applicationYaml).contains("show-details: never");
	}
}
