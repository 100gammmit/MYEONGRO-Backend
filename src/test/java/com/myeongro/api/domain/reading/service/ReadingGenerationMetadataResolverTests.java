package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.entity.ReadingKind;

class ReadingGenerationMetadataResolverTests {

	@Test
	void delegatesMetadataResolutionToTheKindRouter() {
		ReadingGeneratorRouter router = org.mockito.Mockito.mock(ReadingGeneratorRouter.class);
		ReadingGenerationMetadata expected = new ReadingGenerationMetadata(
			"openai", "gpt-test", "common+cards+choice"
		);
		when(router.metadata(ReadingKind.TAROT, "choice_five_card")).thenReturn(expected);
		ReadingGenerationMetadataResolver resolver = new ReadingGenerationMetadataResolver(router);

		assertThat(resolver.resolve(ReadingKind.TAROT, "choice_five_card")).isEqualTo(expected);
		verify(router).metadata(ReadingKind.TAROT, "choice_five_card");
	}
}
