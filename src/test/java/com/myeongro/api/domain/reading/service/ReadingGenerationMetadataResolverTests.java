package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

class ReadingGenerationMetadataResolverTests {

	@Test
	void identifiesTheActualSpreadPromptComposition() {
		TarotPromptCatalog catalog = org.mockito.Mockito.mock(TarotPromptCatalog.class);
		when(catalog.version(TarotSpreadType.CHOICE_FIVE_CARD))
			.thenReturn("common-v1+cards-v1+choice-v1");
		ReadingGenerationMetadataResolver resolver =
			new ReadingGenerationMetadataResolver("gpt-test", catalog);

		assertThat(resolver.resolve(ReadingKind.TAROT, TarotSpreadType.CHOICE_FIVE_CARD))
			.isEqualTo(new ReadingGenerationMetadata(
				"openai", "gpt-test", "common-v1+cards-v1+choice-v1"
			));
	}
}
