package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.saju.service.OpenAiSajuReadingGenerator;

class ReadingGeneratorRouterTests {

	private final OpenAiReadingGenerator openAiGenerator = mock(OpenAiReadingGenerator.class);
	private final OpenAiSajuReadingGenerator sajuGenerator = mock(OpenAiSajuReadingGenerator.class);
	private final ReadingGeneratorRouter router = new ReadingGeneratorRouter(
		openAiGenerator,
		sajuGenerator
	);

	@Test
	void routesTarotOnlyToOpenAiGenerator() {
		Map<String, Object> input = Map.of("cards", "three cards");
		GeneratedReading expected = mock(GeneratedReading.class);
		when(openAiGenerator.generate(ReadingKind.TAROT, TarotSpreadType.MIND_THREE_CARD, "질문", input))
			.thenReturn(expected);

		GeneratedReading actual = router.generate(
			ReadingKind.TAROT, TarotSpreadType.MIND_THREE_CARD, "질문", input
		);

		assertThat(actual).isSameAs(expected);
		verify(openAiGenerator).generate(
			ReadingKind.TAROT, TarotSpreadType.MIND_THREE_CARD, "질문", input
		);
		verify(sajuGenerator, never()).generate(
			ReadingKind.TAROT, TarotSpreadType.MIND_THREE_CARD, "질문", input
		);
	}

	@Test
	void routesSajuOnlyToOpenAiSajuGenerator() {
		Map<String, Object> input = Map.of("profile", "birth profile");
		GeneratedReading expected = mock(GeneratedReading.class);
		when(sajuGenerator.generate(ReadingKind.SAJU, null, "질문", input))
			.thenReturn(expected);

		GeneratedReading actual = router.generate(ReadingKind.SAJU, null, "질문", input);

		assertThat(actual).isSameAs(expected);
		verify(sajuGenerator).generate(ReadingKind.SAJU, null, "질문", input);
		verify(openAiGenerator, never()).generate(ReadingKind.SAJU, null, "질문", input);
	}
}
