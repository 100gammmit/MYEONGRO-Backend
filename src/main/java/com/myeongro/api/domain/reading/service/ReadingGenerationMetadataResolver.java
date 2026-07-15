package com.myeongro.api.domain.reading.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

@Component
public class ReadingGenerationMetadataResolver {

	private static final ReadingGenerationMetadata SAJU_DEMO_METADATA =
		new ReadingGenerationMetadata(
			"demo",
			"deterministic-demo",
			"demo-saju-v1"
		);

	private final String openAiModel;
	private final TarotPromptCatalog promptCatalog;

	public ReadingGenerationMetadataResolver(
		@Value("${app.reading.openai.model:gpt-5.4-mini}") String openAiModel,
		TarotPromptCatalog promptCatalog
	) {
		this.openAiModel = openAiModel;
		this.promptCatalog = promptCatalog;
	}

	public ReadingGenerationMetadata resolve(
		ReadingKind kind,
		TarotSpreadType spreadType
	) {
		if (kind == ReadingKind.TAROT) {
			return new ReadingGenerationMetadata(
				"openai",
				openAiModel,
				promptCatalog.version(spreadType)
			);
		}
		return SAJU_DEMO_METADATA;
	}
}
