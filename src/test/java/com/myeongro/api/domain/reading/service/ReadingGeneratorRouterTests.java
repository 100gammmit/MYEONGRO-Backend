package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;

class ReadingGeneratorRouterTests {

	private ReadingGenerationHandler tarotHandler;
	private ReadingGenerationHandler sajuHandler;
	private ReadingGeneratorRouter router;

	@BeforeEach
	void setUp() {
		tarotHandler = mock(ReadingGenerationHandler.class);
		sajuHandler = mock(ReadingGenerationHandler.class);
		when(tarotHandler.kind()).thenReturn(ReadingKind.TAROT);
		when(sajuHandler.kind()).thenReturn(ReadingKind.SAJU);
		router = new ReadingGeneratorRouter(List.of(tarotHandler, sajuHandler));
	}

	@Test
	void routesTarotOnlyToItsRegisteredHandler() {
		Map<String, Object> input = Map.of("cards", "three cards");
		GeneratedReading expected = mock(GeneratedReading.class);
		when(tarotHandler.generate(
			ReadingKind.TAROT, "mind_three_card", "질문", input
		)).thenReturn(expected);

		GeneratedReading actual = router.generate(
			ReadingKind.TAROT, "mind_three_card", "질문", input
		);

		assertThat(actual).isSameAs(expected);
		verify(tarotHandler).generate(
			ReadingKind.TAROT, "mind_three_card", "질문", input
		);
		verify(sajuHandler, never()).generate(
			ReadingKind.TAROT, "mind_three_card", "질문", input
		);
	}

	@Test
	void routesSajuOnlyToItsRegisteredHandler() {
		Map<String, Object> input = Map.of("profile", "birth profile");
		GeneratedReading expected = mock(GeneratedReading.class);
		when(sajuHandler.generate(ReadingKind.SAJU, null, "질문", input))
			.thenReturn(expected);

		assertThat(router.generate(ReadingKind.SAJU, null, "질문", input))
			.isSameAs(expected);
		verify(sajuHandler).generate(ReadingKind.SAJU, null, "질문", input);
		verify(tarotHandler, never()).generate(ReadingKind.SAJU, null, "질문", input);
	}

	@Test
	void resolvesMetadataThroughTheSameHandlerRegistry() {
		ReadingGenerationMetadata expected = new ReadingGenerationMetadata(
			"openai", "gpt-test", "saju-v1"
		);
		when(sajuHandler.metadata(null)).thenReturn(expected);

		assertThat(router.metadata(ReadingKind.SAJU, null)).isEqualTo(expected);
	}
}
