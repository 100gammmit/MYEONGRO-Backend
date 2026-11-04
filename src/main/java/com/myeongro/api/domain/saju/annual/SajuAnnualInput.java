package com.myeongro.api.domain.saju.annual;

import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.saju.calculation.SajuMonthlyCalendar;
import com.myeongro.api.domain.saju.model.SajuFocusArea;
import com.myeongro.api.domain.saju.service.SajuInterpretationInputMapper;

/** Prepared only from a server-calculated snapshot. Never contains the birth profile. */
public record SajuAnnualInput(String question, String focusArea, SajuAnnualCalendar calendar,
	Map<String, Object> trustedCalculation) {
	public SajuAnnualInput { trustedCalculation = Map.copyOf(trustedCalculation); }

	public static SajuAnnualInput from(String question, String focusArea, int targetYear,
		Map<String, Object> snapshot, ObjectMapper mapper) {
		String normalized = question == null ? "" : question.trim();
		if (normalized.length() > 300) throw new IllegalArgumentException("Question exceeds 300 characters");
		SajuFocusArea.fromValue(focusArea);
		if (!(snapshot.get("targetYear") instanceof Integer year) || year != targetYear
			|| !(snapshot.get("annualFortune") instanceof Map<?, ?> annual)
			|| !(annual.get("year") instanceof Integer annualYear) || annualYear != targetYear) {
			throw new IllegalArgumentException("Frozen snapshot year mismatch");
		}
		new com.myeongro.api.domain.reading.service.DirectIdentifierInputGuard().validate(
			new com.myeongro.api.domain.saju.service.SajuCalculationInput(7, normalized,
				SajuFocusArea.fromValue(focusArea), Map.of(), targetYear));
		var monthly = mapper.convertValue(snapshot.get("monthlyCalendar"), SajuMonthlyCalendar.class);
		var calendar = SajuAnnualCalendar.from(monthly);
		if (calendar.targetYear() != targetYear) throw new IllegalArgumentException("Annual year mismatch");
		if (!(snapshot.get("dayMaster") instanceof String dayMaster && !dayMaster.isBlank())
			&& calendar.periods().stream().anyMatch(p -> p.stemTenGod() != null)) {
			throw new IllegalArgumentException("Monthly ten god requires a certain day master");
		}
		// Stored v6 pillars use ganZhi, while the interpretation mapper accepts full calculated pillars.
		// Split the already calculated characters only; do not calculate new hidden stems or relations.
		Map<String, Object> mappedSnapshot = new LinkedHashMap<>(snapshot);
		Map<String, Object> pillars = new LinkedHashMap<>();
		if (!(snapshot.get("pillars") instanceof Map<?, ?> rawPillars)) throw new IllegalArgumentException("Pillars required");
		for (String name : java.util.List.of("year", "month", "day", "time")) {
			if (rawPillars.get(name) instanceof Map<?, ?> pillar) {
				if (!(pillar.get("ganZhi") instanceof String ganZhi) || ganZhi.length() != 2) throw new IllegalArgumentException("Calculated ganZhi required");
				Map<String, Object> value = new LinkedHashMap<>();
				value.put("stem", ganZhi.substring(0, 1)); value.put("branch", ganZhi.substring(1, 2));
				if (pillar.get("stemTenGod") != null) value.put("stemTenGod", pillar.get("stemTenGod"));
				pillars.put(name, value);
			}
		}
		mappedSnapshot.put("pillars", pillars);
		if (snapshot.get("currentLuckCycle") instanceof Map<?, ?> cycle) {
			mappedSnapshot.put("luckCycle", Map.of("periods", java.util.List.of(cycle)));
		}
		Map<String, Object> trusted = new LinkedHashMap<>(new SajuInterpretationInputMapper().map(mappedSnapshot, targetYear));
		if (!trusted.containsKey("dayMaster")) trusted.remove("tenGods");
		String previousYearGanZhi = null;
		for (var period : calendar.periods()) {
			Map<String, Object> value = new LinkedHashMap<>();
			value.put("yearGanZhi", period.yearGanZhi());
			value.put("monthGanZhi", period.monthGanZhi());
			value.put("yearContext", period.yearGanZhi().equals(annual.get("ganZhi")) ? "target_year" : "previous_year");
			value.put("yearChangedFromPreviousPeriod", previousYearGanZhi != null && !previousYearGanZhi.equals(period.yearGanZhi()));
			if (period.stemTenGod() != null) value.put("stemTenGod", period.stemTenGod());
			trusted.put("monthlyFlow." + period.id(), Map.copyOf(value));
			previousYearGanZhi = period.yearGanZhi();
		}
		return new SajuAnnualInput(normalized, focusArea, calendar, trusted);
	}
}
