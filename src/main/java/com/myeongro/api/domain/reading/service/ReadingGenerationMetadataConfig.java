package com.myeongro.api.domain.reading.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReadingGenerationMetadataConfig {

	@Bean
	ReadingGenerationMetadata readingGenerationMetadata(
		@Value("${app.reading.generator:demo}") String generator,
		@Value("${app.reading.openai.model:gpt-5.4-mini}") String openAiModel,
		@Value("${app.reading.prompt-version:mvp-2026-06-16}") String promptVersion
	) {
		if ("openai".equals(generator)) {
			return new ReadingGenerationMetadata("openai", openAiModel, promptVersion);
		}
		return new ReadingGenerationMetadata(
			"demo",
			"deterministic-demo",
			promptVersion
		);
	}
}
