package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.entity.ReadingKind;

class ReadingGenerationMetadataResolverTests {

	private final ReadingGenerationMetadataResolver resolver =
		new ReadingGenerationMetadataResolver("gpt-test", "tarot-prompt-v1");

	@Test
	void resolvesOpenAiMetadataForTarot() {
		assertThat(resolver.resolve(ReadingKind.TAROT))
			.isEqualTo(new ReadingGenerationMetadata(
				"openai",
				"gpt-test",
				"tarot-prompt-v1"
			));
	}

	@Test
	void resolvesDemoMetadataForSaju() {
		assertThat(resolver.resolve(ReadingKind.SAJU))
			.isEqualTo(new ReadingGenerationMetadata(
				"demo",
				"deterministic-demo",
				"demo-saju-v1"
			));
	}
}
