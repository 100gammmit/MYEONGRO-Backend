package com.myeongro.api.domain.saju.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.saju.result.SajuReadingResult;
import com.myeongro.api.domain.saju.result.SajuReadingSection;
import com.myeongro.api.domain.reading.service.ReadingMode;

class SajuReadingResultValidatorTests {

	private final SajuReadingResultValidator validator = new SajuReadingResultValidator();

	@Test
	void acceptsEvidenceBoundResultWithFixedShape() {
		assertThatCode(() -> validator.validate(
			result("차분히 가능성을 살펴보세요", "annualFlow"),
			2026,
			trusted("exact")
		)).doesNotThrowAnyException();
	}

	@Test
	void rejectsUnknownEvidenceAndMissingLuckCycleEvidence() {
		assertThatThrownBy(() -> validator.validate(
			result("차분히 가능성을 살펴보세요", "currentLuckCycle"),
			2026,
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
			valid.annualReading(), valid.questionReading(), valid.disclaimer()
		);

		assertThatThrownBy(() -> validator.validate(
			duplicate, 2026, trusted("exact")
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("evidence");
	}

	@Test
	void rejectsWrongSectionOrderOrYear() {
		SajuReadingResult valid = result("차분히 가능성을 살펴보세요", "annualFlow");
		SajuReadingResult wrongOrder = new SajuReadingResult(
			valid.title(), valid.summary(),
			List.of(
				valid.natalSections().get(1), valid.natalSections().get(0),
				valid.natalSections().get(2), valid.natalSections().get(3)
			),
			valid.annualReading(), valid.questionReading(), valid.disclaimer()
		);
		assertThatThrownBy(() -> validator.validate(wrongOrder, 2026, trusted("exact")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> validator.validate(valid, 2027, trusted("exact")))
			.isInstanceOf(IllegalArgumentException.class);
	}


	@Test
	void rejectsRedirectedQuestionInStandardMode() {
		SajuReadingResult valid = result("차분히 가능성을 살펴보세요", "annualFlow");
		SajuReadingResult invalid = new SajuReadingResult(
			ReadingMode.STANDARD, true, valid.title(), valid.summary(), valid.natalSections(),
			valid.annualReading(), valid.questionReading(), valid.disclaimer()
		);

		assertThatThrownBy(() -> validator.validate(invalid, 2026, trusted("exact")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("fortune reading mode");
	}

	@Test
	void acceptsACarefulAnswerThatNegatesCertaintyWhenBirthTimeIsUncertain() {
		assertThatCode(() -> validator.validate(
			result("반드시 성공한다고 확답할 수는 없어요", "annualFlow"),
			2026,
			trusted("unknown")
		)).doesNotThrowAnyException();
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
				"지금의 질문", "선택지를 점검해 보세요", List.of("dayMaster")
			),
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
			"uncertainty", Map.of(
				"varyingFields", "exact".equals(precision) ? List.of() : List.of("pillars.time")
			)
		);
	}
}
