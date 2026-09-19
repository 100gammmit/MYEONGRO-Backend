package com.myeongro.api.domain.saju.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.service.PreparedReadingInput;
import com.myeongro.api.domain.reading.service.ReadingSchemaVersions;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.AnnualFortune;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.LuckCycle;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.MajorLuckPeriod;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillar;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Pillars;
import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot.Uncertainty;
import com.myeongro.api.domain.saju.model.SajuFocusArea;

class SajuReadingInputAssemblerTests {

	private final SajuCalculationService calculationService =
		org.mockito.Mockito.mock(SajuCalculationService.class);
	private final SajuReadingInputAssembler assembler = new SajuReadingInputAssembler(
		calculationService,
		new SajuInterpretationInputMapper(),
		new SajuStoredCalculationMapper(),
		new ObjectMapper()
	);

	@Test
	void separatesTransientBirthDataAiInputAndMinimalStoredInput() {
		SajuCalculationInput base = baseInput();
		when(calculationService.calculate(profile(), 2026)).thenReturn(snapshot());

		PreparedReadingInput assembled = assembler.assemble(base, 2026);

		assertThat(assembled.schemaVersion()).isEqualTo(5);
		assertThat(assembled.aiPayload())
			.containsOnlyKeys("focusArea", "targetYear", "calculation")
			.doesNotContainKeys("birthProfile", "calculationSnapshot");
		assertThat(assembled.storedPayload())
			.containsOnlyKeys("targetYear", "calculationSnapshot")
			.doesNotContainKeys("question", "focusArea", "birthProfile");

		Map<?, ?> aiCalculation = (Map<?, ?>)assembled.aiPayload().get("calculation");
		@SuppressWarnings("unchecked")
		Map<String, Object> storedCalculation =
			(Map<String, Object>)assembled.storedPayload().get("calculationSnapshot");
		assertThat(aiCalculation).isNotSameAs(storedCalculation);
		assertThat(aiCalculation.toString())
			.doesNotContain("birthDate", "birthTime", "provinceCode", "precision",
				"candidateCount", "candidateZoneOffsets", "BIRTH_TIME_UNKNOWN");
		assertThat(storedCalculation)
			.containsKeys("calculationVersion", "engine", "engineVersion",
				"cityCatalogVersion", "pillars", "currentLuckCycle", "annualFortune")
			.doesNotContainKeys("timeCorrection", "luckCycle");
		assertThat(storedCalculation.toString())
			.doesNotContain("startAge", "endAge", "direction", "precision",
				"candidateCount", "candidateZoneOffsets", "APPROXIMATE_BIRTH_TIME");
	}

	private SajuCalculationInput baseInput() {
		return new SajuCalculationInput(
			ReadingSchemaVersions.SAJU,
			"올해 흐름이 궁금해요",
			SajuFocusArea.CAREER,
			profile()
		);
	}

	private Map<String, Object> profile() {
		return Map.of(
			"calendarType", "solar",
			"birthDate", "1992-08-17",
			"birthTime", "12:00",
			"birthTimePrecision", "approximate",
			"provinceCode", "36",
			"cityCode", "36110",
			"luckDirectionBasis", "male"
		);
	}

	private SajuCalculationSnapshot snapshot() {
		Pillar pillar = new Pillar("壬申", "壬", "申", "watermetal", "正印", List.of("正官"));
		LuckCycle luckCycle = new LuckCycle(
			"forward", "2023-01-01", 30, 2,
			List.of(
				new MajorLuckPeriod(2013, 2022, 20, 29, "甲子"),
				new MajorLuckPeriod(2023, 2032, 30, 39, "乙丑")
			)
		);
		return new SajuCalculationSnapshot(
			"saju-ko-v4", "lunar-java", "1.7.7", "kr-admin-v1", 2026,
			null, new Pillars(pillar, pillar, pillar, null), "乙",
			Map.of("wood", 1, "fire", 2, "earth", 1, "metal", 2, "water", 2),
			List.of(), luckCycle,
			new AnnualFortune(2026, "丙午", "伤官"),
			List.of("APPROXIMATE_BIRTH_TIME", "TIME_PILLAR_VARIES"),
			new Uncertainty(
				"approximate", 121, "1992-08-17T11:00", "1992-08-17T13:00",
				List.of("timePillar"), List.of("+09:00")
			)
		);
	}
}
