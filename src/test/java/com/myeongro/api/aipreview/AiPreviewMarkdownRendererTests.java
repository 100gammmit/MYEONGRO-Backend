package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AiPreviewMarkdownRendererTests {

	private final AiPreviewMarkdownRenderer renderer = new AiPreviewMarkdownRenderer(
		ZoneId.of("Asia/Seoul")
	);

	@Test
	void rendersTarotReadingForHumans() {
		AiPreviewReport report = report(
			"tarot",
			"mind_three_card",
			Map.of(
				"title", "마음 리딩",
				"summary", "마음의 흐름을 살펴봅니다.",
				"sections", List.of(Map.of(
					"heading", "현재 감정",
					"body", "감정을 천천히 확인해 보세요."
				)),
				"guidance", List.of("사실과 추측을 나누어 적어 보세요."),
				"disclaimer", "자기 성찰을 위한 리딩입니다."
			)
		);

		String markdown = renderer.render(report);

		assertThat(markdown).contains(
			"# 마음 리딩",
			"## 현재 감정",
			"## 실천 가이드",
			"1. 사실과 추측을 나누어 적어 보세요.",
			"> 자기 성찰을 위한 리딩입니다.",
			"- 생성 시간: 2026-08-10 09:00:00 +09:00",
			"- 소요 시간: 8.576초"
		);
	}

	@Test
	void rendersSajuSectionsAndEvidence() {
		AiPreviewReport report = report(
			"saju",
			null,
			Map.of(
				"title", "커리어 리딩",
				"summary", "현실적인 조건을 함께 살펴봅니다.",
				"natalSections", List.of(Map.of(
					"heading", "일하는 방식",
					"body", "역할과 조건을 구체적으로 비교해 보세요.",
					"evidenceKeys", List.of("tenGods", "elementBalance")
				)),
				"annualReading", Map.of(
					"heading", "올해의 흐름",
					"body", "결과물을 정리해 보세요.",
					"evidenceKeys", List.of("annualFlow")
				),
				"questionReading", Map.of(
					"heading", "질문에 대한 리딩",
					"body", "실제 채용 조건을 확인해 보세요."
				)
			)
		);

		String markdown = renderer.render(report);

		assertThat(markdown).contains(
			"## 일하는 방식",
			"근거: `tenGods`, `elementBalance`",
			"## 올해의 흐름",
			"근거: `annualFlow`",
			"## 질문에 대한 리딩"
		);
	}

	private AiPreviewReport report(String kind, String spreadType, Map<String, Object> result) {
		return new AiPreviewReport(
			"20260810-090000-" + kind,
			"test-case",
			kind,
			spreadType,
			"gpt-test",
			"prompt-v1",
			"abc123",
			Instant.parse("2026-08-10T00:00:00Z"),
			8_576L,
			result
		);
	}
}
