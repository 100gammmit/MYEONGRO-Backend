package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.reading.service.ReadingGenerator;
import com.myeongro.api.domain.reading.service.SajuPromptCatalog;
import com.myeongro.api.domain.reading.service.TarotPromptCatalog;

class AiPreviewRunnerPreflightTests {

	@Test
	void rejectsMissingChoiceOptionsBeforeCallingGenerator() {
		ReadingGenerator readingGenerator = mock(ReadingGenerator.class);
		AiPreviewCase previewCase = new AiPreviewCase(
			"invalid-choice",
			ReadingKind.TAROT,
			TarotSpreadType.CHOICE_FIVE_CARD,
			"어떤 선택을 해야 할까요?",
			Map.of("cards", List.of(
				card("major-00-fool", "desire"),
				card("major-18-moon", "fear"),
				card("major-09-hermit", "core_value"),
				card("major-07-chariot", "option_a"),
				card("major-21-world", "option_b")
			))
		);

		AiPreviewRunner runner = runner(readingGenerator, previewCase, "tarot", "invalid-choice");

		assertThatThrownBy(runner::run).hasMessageContaining("input structure");
		verifyNoInteractions(readingGenerator);
	}

	@Test
	void rejectsUnsupportedSajuFocusAreaBeforeCallingGenerator() {
		ReadingGenerator readingGenerator = mock(ReadingGenerator.class);
		AiPreviewCase previewCase = new AiPreviewCase(
			"invalid-saju",
			ReadingKind.SAJU,
			null,
			"무엇을 살펴보면 좋을까요?",
			Map.of(
				"focusArea", "unknown",
				"targetYear", 2026,
				"calculationSnapshot", Map.of()
			)
		);

		AiPreviewRunner runner = runner(readingGenerator, previewCase, "saju", "invalid-saju");

		assertThatThrownBy(runner::run).hasMessageContaining("관심 분야");
		verifyNoInteractions(readingGenerator);
	}

	private AiPreviewRunner runner(
		ReadingGenerator readingGenerator,
		AiPreviewCase previewCase,
		String kind,
		String caseId
	) {
		AiPreviewCaseLoader caseLoader = mock(AiPreviewCaseLoader.class);
		when(caseLoader.load(kind, caseId)).thenReturn(previewCase);
		return new AiPreviewRunner(
			readingGenerator,
			mock(TarotPromptCatalog.class),
			mock(SajuPromptCatalog.class),
			caseLoader,
			new TarotPreviewInputValidator(),
			new SajuPreviewInputValidator(),
			mock(AiPreviewReportWriter.class),
			new AiPreviewLabelFactory(Clock.fixed(
				Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC
			)),
			kind,
			caseId,
			true,
			"gpt-test"
		);
	}

	private Map<String, Object> card(String cardId, String position) {
		return Map.of("cardId", cardId, "position", position, "reversed", false);
	}
}
