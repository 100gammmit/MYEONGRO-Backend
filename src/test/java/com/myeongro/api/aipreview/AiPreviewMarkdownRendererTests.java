package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AiPreviewMarkdownRendererTests {

	@Test
	void rendersNormalSummaryAfterBodyAndNeverRendersObsoleteGuidance() {
		String markdown = render(Map.of(
			"title", "정상 결과", "summary", "핵심 답과 제안",
			"sections", List.of(Map.of("heading", "해석", "body", "카드 본문")),
			"guidance", List.of("예전 실천 과제")
		));
		assertThat(markdown).contains("## 리딩 요약", "핵심 답과 제안")
			.doesNotContain("예전 실천 과제", "실천 가이드");
		assertThat(markdown.indexOf("카드 본문")).isLessThan(markdown.indexOf("핵심 답과 제안"));
	}

	@Test
	void retainsDeclinedMessageAndGuidance() {
		assertThat(render(Map.of(
			"resultType", "declined", "message", "거절 안내",
			"guidance", List.of("안전 안내")
		))).contains("거절 안내", "안전 안내").doesNotContain("리딩 요약");
	}

	private String render(Map<String, Object> result) {
		return new AiPreviewMarkdownRenderer(ZoneId.of("Asia/Seoul")).render(new AiPreviewReport(
			"test", "synthetic", "tarot", null, "test", "test", "test", Instant.EPOCH, 1L, result
		));
	}

	@Test
	void rendersSajuEvidenceKeysAsKoreanLabels() {
		List<String> evidenceKeys = List.of(
			"pillars",
			"dayMaster",
			"elementBalance",
			"tenGods",
			"interactions",
			"currentLuckCycle",
			"annualFlow",
			"limitations",
			"uncertainty"
		);
		Map<String, Object> section = Map.of(
			"heading", "테스트 해석",
			"body", "테스트 본문",
			"evidenceKeys", evidenceKeys
		);
		AiPreviewReport report = new AiPreviewReport(
			"test-label",
			"test-case",
			"saju",
			null,
			"test-model",
			"test-prompt",
			"test-sha",
			Instant.EPOCH,
			1L,
			Map.of("title", "테스트 결과", "natalSections", List.of(section))
		);

		String markdown = new AiPreviewMarkdownRenderer(ZoneId.of("Asia/Seoul")).render(report);

		assertThat(markdown).contains(
			"근거: 명식의 기둥, 일간, 오행 분포, 십성 관계, 간지 관계, 현재 대운, 해당 연도 세운, 계산 제한사항, 출생 시각 불확실성"
		);
		assertThat(markdown).doesNotContain("`pillars`", "`dayMaster`", "`annualFlow`");
	}
}
