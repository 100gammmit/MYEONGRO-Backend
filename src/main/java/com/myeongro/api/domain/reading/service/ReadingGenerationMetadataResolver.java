package com.myeongro.api.domain.reading.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.entity.ReadingKind;

@Component
public class ReadingGenerationMetadataResolver {

	private static final ReadingGenerationMetadata SAJU_DEMO_METADATA =
		new ReadingGenerationMetadata(
			"demo",
			"deterministic-demo",
			"demo-saju-v1"
		);

	private final ReadingGenerationMetadata tarotMetadata;

	public ReadingGenerationMetadataResolver(
		@Value("${app.reading.openai.model:gpt-5.4-mini}") String openAiModel,
		@Value("${app.reading.prompts.tarot-version:major-arcana-3card-ko-v1}")
		String tarotPromptVersion
	) {
		this.tarotMetadata = new ReadingGenerationMetadata(
			"openai",
			openAiModel,
			tarotPromptVersion
		);
	}

	public ReadingGenerationMetadata resolve(ReadingKind kind) {
		if (kind == ReadingKind.TAROT) {
			return tarotMetadata;
		}
		return SAJU_DEMO_METADATA;
	}
}
