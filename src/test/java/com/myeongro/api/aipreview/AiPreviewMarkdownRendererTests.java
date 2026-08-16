package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AiPreviewMarkdownRendererTests {

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
