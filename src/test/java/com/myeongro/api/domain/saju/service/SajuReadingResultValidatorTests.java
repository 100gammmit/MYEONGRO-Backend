package com.myeongro.api.domain.saju.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.saju.result.SajuReadingResult;
import com.myeongro.api.domain.saju.result.SajuReadingSection;

class SajuReadingResultValidatorTests {

	private final SajuReadingResultValidator validator = new SajuReadingResultValidator();

	@Test
	void acceptsEvidenceBoundResultWithFixedShape() {
		assertThatCode(() -> validator.validate(
			result("차분히 가능성을 살펴보세요", "annualFlow"),
			2026,
			"career",
			trusted("exact")
		)).doesNotThrowAnyException();
	}

	@Test
	void rejectsUnknownEvidenceAndMissingLuckCycleEvidence() {
		assertThatThrownBy(() -> validator.validate(
			result("차분히 가능성을 살펴보세요", "currentLuckCycle"),
			2026,
			"career",
			trusted("exact")
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("evidence");
	}

	@Test
	void rejectsDuplicateEvidenceKeysOutsideTheOpenAiSchema() {
		SajuReadingResult valid = result("차분히 가능성을 살펴보세요", "annualFlow");
		SajuReadingSection core = valid.natalSections().getFirst();
		SajuReadingSection duplicateCore = new SajuReadingSection(
			core.id(), core.heading(), core.body(), List.of("dayMaster", "dayMaster")
		);
		SajuReadingResult duplicate = new SajuReadingResult(
			valid.title(), valid.summary(),
			List.of(
				duplicateCore,
				valid.natalSections().get(1),
				valid.natalSections().get(2),
				valid.natalSections().get(3)
			),
			valid.annualReading(), valid.questionReading(), valid.guidance(), valid.disclaimer()
		);

		assertThatThrownBy(() -> validator.validate(
			duplicate, 2026, "career", trusted("exact")
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("evidence");
	}

	@Test
	void rejectsWrongSectionOrderYearOrFocusArea() {
		SajuReadingResult valid = result("차분히 가능성을 살펴보세요", "annualFlow");
		SajuReadingResult wrongOrder = new SajuReadingResult(
			valid.title(), valid.summary(),
			List.of(
				valid.natalSections().get(1), valid.natalSections().get(0),
				valid.natalSections().get(2), valid.natalSections().get(3)
			),
			valid.annualReading(), valid.questionReading(), valid.guidance(), valid.disclaimer()
		);
		assertThatThrownBy(() -> validator.validate(wrongOrder, 2026, "career", trusted("exact")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> validator.validate(valid, 2027, "career", trusted("exact")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> validator.validate(valid, 2026, "self", trusted("exact")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMoreThanOneGuidanceItem() {
		SajuReadingResult valid = result("차분히 가능성을 살펴보세요", "annualFlow");
		SajuReadingResult duplicateGuidance = new SajuReadingResult(
			valid.readingMode(), valid.title(), valid.summary(), valid.natalSections(),
			valid.annualReading(), valid.questionReading(),
			List.of("첫 번째 제안입니다", "두 번째 제안입니다"), valid.disclaimer()
		);

		assertThatThrownBy(() -> validator.validate(
			duplicateGuidance, 2026, "career", trusted("exact")
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("guidance");
	}

	@Test
	void rejectsDefinitiveLanguageWhenBirthTimeIsUncertain() {
		assertThatThrownBy(() -> validator.validate(
			result("올해 반드시 성공합니다", "annualFlow"),
			2026,
			"career",
			trusted("unknown")
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("definitive");
	}

	@Test
	void rejectsDefinitiveLanguageWhenExactCivilTimeHasMultipleInstants() {
		Map<String, Object> trusted = new java.util.LinkedHashMap<>(trusted("exact"));
		trusted.put("uncertainty", Map.of("precision", "exact", "candidateCount", 2));
		assertThatThrownBy(() -> validator.validate(
			result("올해 반드시 성공합니다", "annualFlow"),
			2026,
			"career",
			trusted
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("definitive");
	}

	private SajuReadingResult result(String summary, String annualEvidence) {
		return new SajuReadingResult(
			"변화를 준비하며 기준을 세우는 해",
			summary,
			List.of(
				section("core", "중심", "중심을 살펴봅니다", "dayMaster"),
				section("strengths", "강점", "균형을 살펴봅니다", "elementBalance"),
				section("relationship", "관계", "상호작용을 살펴봅니다", "interactions"),
				section("work", "일", "일의 방식을 살펴봅니다", "tenGods")
			),
			new SajuReadingResult.AnnualReading(
				2026, "2026년의 흐름", "연간 흐름을 살펴봅니다", List.of(annualEvidence)
			),
			new SajuReadingResult.QuestionReading(
				"career", "지금의 질문", "선택지를 점검해 보세요", List.of("dayMaster")
			),
			List.of("작은 실험부터 해보세요"),
			"이 리딩은 오락과 자기성찰을 위한 참고입니다."
		);
	}

	private SajuReadingSection section(String id, String heading, String body, String evidence) {
		return new SajuReadingSection(id, heading, body, List.of(evidence));
	}

	private Map<String, Object> trusted(String precision) {
		return Map.of(
			"dayMaster", "甲",
			"elementBalance", Map.of("wood", 2),
			"tenGods", Map.of("year", Map.of("stem", "편재")),
			"interactions", List.of(),
			"annualFlow", Map.of("year", 2026),
			"limitations", List.of(),
			"uncertainty", Map.of("precision", precision, "candidateCount", 1)
		);
	}
}
