package com.myeongro.api.domain.reading.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

@Component
public class ReadingGenerationMetadataResolver {

	private final String openAiModel;
	private final TarotPromptCatalog tarotPromptCatalog;
	private final SajuPromptCatalog sajuPromptCatalog;

	public ReadingGenerationMetadataResolver(
		@Value("${app.reading.openai.model}") String openAiModel,
		TarotPromptCatalog tarotPromptCatalog,
		SajuPromptCatalog sajuPromptCatalog
	) {
		this.openAiModel = openAiModel;
		this.tarotPromptCatalog = tarotPromptCatalog;
		this.sajuPromptCatalog = sajuPromptCatalog;
	}

	public ReadingGenerationMetadata resolve(
		ReadingKind kind,
		TarotSpreadType spreadType
	) {
		if (kind == ReadingKind.TAROT) {
			return new ReadingGenerationMetadata(
				"openai",
				openAiModel,
				tarotPromptCatalog.version(spreadType)
			);
		}
		return new ReadingGenerationMetadata(
			"openai",
			openAiModel,
			sajuPromptCatalog.version()
		);
	}
}
