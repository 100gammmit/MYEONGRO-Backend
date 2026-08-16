package com.myeongro.api.domain.saju.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SajuInterpretationInputMapperTests {

	private final SajuInterpretationInputMapper mapper = new SajuInterpretationInputMapper();

	@Test
	void localizesCalculationTermsAndKeepsOnlyTheTargetYearsLuckPeriod() {
		Map<String, Object> actual = mapper.map(snapshot(), 2026);

		assertThat(actual).containsOnlyKeys(
			"pillars", "dayMaster", "elementBalance", "tenGods", "interactions",
			"currentLuckCycle", "annualFlow", "limitations", "uncertainty"
		);
		assertThat(actual).containsEntry("dayMaster", "을목");
		assertThat(actual.get("elementBalance")).isEqualTo(Map.of(
			"목", 1, "화", 0, "토", 3, "금", 2, "수", 2
		));

		@SuppressWarnings("unchecked")
		Map<String, Object> pillars = (Map<String, Object>)actual.get("pillars");
		@SuppressWarnings("unchecked")
		Map<String, Object> year = (Map<String, Object>)pillars.get("year");
		assertThat(year)
			.containsEntry("ganZhi", "임신")
			.containsEntry("stem", "임수")
			.containsEntry("branch", "신금")
			.doesNotContainKeys("fiveElements", "stemTenGod", "branchTenGods");

		@SuppressWarnings("unchecked")
		Map<String, Object> current = (Map<String, Object>)actual.get("currentLuckCycle");
		assertThat(current).containsOnly(
			Map.entry("direction", "순행"),
			Map.entry("startYear", 2023),
			Map.entry("endYear", 2032),
			Map.entry("ganZhi", "병오")
		);
		assertThat(actual.toString()).doesNotContain(
			"甲", "乙", "壬", "申", "伤官", "偏财", "stem_combination", "wood"
		);
	}

	@Test
	void omitsLuckCycleWhenNoPeriodContainsTheTargetYear() {
		assertThat(mapper.map(snapshot(), 2045)).doesNotContainKey("currentLuckCycle");
	}

	@Test
	void keepsStableAnnualFlowWhenBirthTimeMakesDayBasedFactsUncertain() {
		Map<String, Object> pillars = new LinkedHashMap<>();
		pillars.put("year", uncertainPillar());
		pillars.put("month", null);
		pillars.put("day", null);
		pillars.put("time", null);
		Map<String, Object> annual = new LinkedHashMap<>();
		annual.put("year", 2026);
		annual.put("ganZhi", "丙午");
		annual.put("stemTenGod", null);
		Map<String, Object> sparse = new LinkedHashMap<>();
		sparse.put("pillars", pillars);
		sparse.put("dayMaster", null);
		sparse.put("fiveElements", Map.of());
		sparse.put("relations", List.of());
		sparse.put("luckCycle", null);
		sparse.put("annualFortune", annual);
		sparse.put("limitations", List.of("BIRTH_TIME_UNKNOWN", "DAY_PILLAR_UNCERTAIN"));
		sparse.put("uncertainty", Map.of(
			"precision", "unknown", "candidateCount", 1440,
			"varyingFields", List.of("pillars.day", "annualFortune.stemTenGod"),
			"candidateZoneOffsets", List.of()
		));

		Map<String, Object> actual = mapper.map(sparse, 2026);

		assertThat(actual).containsOnlyKeys(
			"pillars", "annualFlow", "limitations", "uncertainty"
		);
		assertThat(actual.get("annualFlow")).isEqualTo(Map.of(
			"year", 2026, "ganZhi", "병오"
		));
	}

	private Map<String, Object> snapshot() {
		Map<String, Object> pillars = new LinkedHashMap<>();
		pillars.put("year", pillar("壬申", "壬", "申", "偏财"));
		pillars.put("month", pillar("戊申", "戊", "申", "七杀"));
		pillars.put("day", pillar("乙丑", "乙", "丑", "比肩"));
		pillars.put("time", null);

		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("calculationVersion", "saju-ko-v1");
		snapshot.put("pillars", pillars);
		snapshot.put("dayMaster", "乙");
		snapshot.put("fiveElements", Map.of(
			"wood", 1, "fire", 0, "earth", 3, "metal", 2, "water", 2
		));
		snapshot.put("relations", List.of(Map.of(
			"type", "stem_combination", "members", List.of("甲", "己")
		)));
		snapshot.put("luckCycle", Map.of(
			"direction", "forward",
			"periods", List.of(
				Map.of("startYear", 2013, "endYear", 2022, "ganZhi", "乙巳"),
				Map.of("startYear", 2023, "endYear", 2032, "ganZhi", "丙午"),
				Map.of("startYear", 2033, "endYear", 2042, "ganZhi", "丁未")
			)
		));
		snapshot.put("annualFortune", Map.of(
			"year", 2026, "ganZhi", "丙午", "stemTenGod", "伤官"
		));
		snapshot.put("limitations", List.of("BIRTH_TIME_UNKNOWN"));
		snapshot.put("uncertainty", Map.of(
			"precision", "unknown", "candidateCount", 1440,
			"varyingFields", List.of("pillars.time"),
			"candidateZoneOffsets", List.of()
		));
		return snapshot;
	}

	private Map<String, Object> pillar(
		String ganZhi,
		String stem,
		String branch,
		String stemTenGod
	) {
		return Map.of(
			"ganZhi", ganZhi,
			"stem", stem,
			"branch", branch,
			"fiveElements", "水金",
			"stemTenGod", stemTenGod,
			"branchTenGods", List.of("偏印", "比肩", "食神")
		);
	}

	private Map<String, Object> uncertainPillar() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("ganZhi", "戊辰");
		result.put("stem", "戊");
		result.put("branch", "辰");
		result.put("fiveElements", "土土");
		result.put("stemTenGod", null);
		result.put("branchTenGods", List.of());
		return result;
	}
}
