package com.myeongro.api.domain.saju.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.saju.calculation.SajuCalculationSnapshot;

@Component
public class SajuStoredCalculationMapper {

	private static final List<String> TRANSIENT_PRECISION_LIMITATIONS = List.of(
		"BIRTH_TIME_UNKNOWN", "APPROXIMATE_BIRTH_TIME"
	);

	public Map<String, Object> map(SajuCalculationSnapshot snapshot) {
		Map<String, Object> stored = new LinkedHashMap<>();
		stored.put("calculationVersion", snapshot.calculationVersion());
		stored.put("engine", snapshot.engine());
		stored.put("engineVersion", snapshot.engineVersion());
		stored.put("cityCatalogVersion", snapshot.cityCatalogVersion());
		stored.put("targetYear", snapshot.targetYear());
		stored.put("pillars", pillars(snapshot.pillars()));
		putIfNotNull(stored, "dayMaster", snapshot.dayMaster());
		stored.put("fiveElements", immutable(snapshot.fiveElements()));
		stored.put("relations", snapshot.relations().stream().map(relation ->
			immutable(new LinkedHashMap<>(Map.of(
				"type", relation.type(),
				"members", List.copyOf(relation.members())
			)))
		).toList());
		currentLuckCycle(snapshot).ifPresent(value -> stored.put("currentLuckCycle", value));
		if (snapshot.annualFortune() != null) {
			Map<String, Object> annual = new LinkedHashMap<>();
			annual.put("year", snapshot.annualFortune().year());
			annual.put("ganZhi", snapshot.annualFortune().ganZhi());
			putIfNotNull(annual, "stemTenGod", snapshot.annualFortune().stemTenGod());
			stored.put("annualFortune", immutable(annual));
		}
		stored.put("limitations", snapshot.limitations().stream()
			.filter(code -> !TRANSIENT_PRECISION_LIMITATIONS.contains(code))
			.toList());
		stored.put("uncertainty", immutable(new LinkedHashMap<>(Map.of(
			"varyingFields", List.copyOf(snapshot.uncertainty().varyingFields())
		))));
		return immutable(stored);
	}

	private Map<String, Object> pillars(SajuCalculationSnapshot.Pillars pillars) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("year", pillar(pillars.year()));
		result.put("month", pillar(pillars.month()));
		result.put("day", pillar(pillars.day()));
		result.put("time", pillar(pillars.time()));
		return immutable(result);
	}

	private Map<String, Object> pillar(SajuCalculationSnapshot.Pillar pillar) {
		if (pillar == null) {
			return null;
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("ganZhi", pillar.ganZhi());
		putIfNotNull(result, "stemTenGod", pillar.stemTenGod());
		return immutable(result);
	}

	private java.util.Optional<Map<String, Object>> currentLuckCycle(
		SajuCalculationSnapshot snapshot
	) {
		if (snapshot.luckCycle() == null) {
			return java.util.Optional.empty();
		}
		return snapshot.luckCycle().periods().stream()
			.filter(period -> period.startYear() <= snapshot.targetYear()
				&& snapshot.targetYear() <= period.endYear())
			.findFirst()
			.map(period -> immutable(new LinkedHashMap<>(Map.of(
				"startYear", period.startYear(),
				"endYear", period.endYear(),
				"ganZhi", period.ganZhi()
			))));
	}

	private void putIfNotNull(Map<String, Object> target, String key, Object value) {
		if (value != null) {
			target.put(key, value);
		}
	}

	private Map<String, Object> immutable(Map<String, ?> value) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(value));
	}
}
