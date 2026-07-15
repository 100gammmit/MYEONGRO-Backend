package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

class ReadingGeneratorRouterTests {

	private final OpenAiReadingGenerator openAiGenerator = mock(OpenAiReadingGenerator.class);
	private final DemoReadingGenerator demoGenerator = mock(DemoReadingGenerator.class);
	private final ReadingGeneratorRouter router = new ReadingGeneratorRouter(
		openAiGenerator,
		demoGenerator
	);

	@Test
	void routesTarotOnlyToOpenAiGenerator() {
		Map<String, Object> input = Map.of("cards", "three cards");
		ReadingResult expected = mock(ReadingResult.class);
		when(openAiGenerator.generate(ReadingKind.TAROT, TarotSpreadType.MIND_THREE_CARD, "질문", input))
			.thenReturn(expected);

		ReadingResult actual = router.generate(
			ReadingKind.TAROT, TarotSpreadType.MIND_THREE_CARD, "질문", input
		);

		assertThat(actual).isSameAs(expected);
		verify(openAiGenerator).generate(
			ReadingKind.TAROT, TarotSpreadType.MIND_THREE_CARD, "질문", input
		);
		verify(demoGenerator, never()).generate(
			ReadingKind.TAROT, TarotSpreadType.MIND_THREE_CARD, "질문", input
		);
	}

	@Test
	void routesSajuOnlyToDemoGenerator() {
		Map<String, Object> input = Map.of("profile", "birth profile");
		ReadingResult expected = mock(ReadingResult.class);
		when(demoGenerator.generate(ReadingKind.SAJU, null, "질문", input))
			.thenReturn(expected);

		ReadingResult actual = router.generate(ReadingKind.SAJU, null, "질문", input);

		assertThat(actual).isSameAs(expected);
		verify(demoGenerator).generate(ReadingKind.SAJU, null, "질문", input);
		verify(openAiGenerator, never()).generate(ReadingKind.SAJU, null, "질문", input);
	}
}
