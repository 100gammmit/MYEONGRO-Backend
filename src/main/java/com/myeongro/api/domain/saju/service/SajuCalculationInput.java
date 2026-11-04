package com.myeongro.api.domain.saju.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.ReadingInputSupport;
import com.myeongro.api.domain.reading.service.ReadingRequestInput;
import com.myeongro.api.domain.saju.model.SajuFocusArea;

public record SajuCalculationInput(
	int schemaVersion,
	String question,
	SajuFocusArea focusArea,
	Map<String, Object> birthProfile,
	int targetYear,
	com.myeongro.api.domain.saju.model.SajuReportType reportType
) implements ReadingRequestInput {
	public SajuCalculationInput(int schemaVersion, String question, SajuFocusArea focusArea, Map<String, Object> birthProfile, int targetYear) {
		this(schemaVersion, question, focusArea, birthProfile, targetYear, com.myeongro.api.domain.saju.model.SajuReportType.BIRTH_ANNUAL_QUESTION);
	}

	public SajuCalculationInput {
		birthProfile = Collections.unmodifiableMap(new LinkedHashMap<>(birthProfile));
	}

	@Override
	public ReadingKind kind() {
		return ReadingKind.SAJU;
	}

	@Override
	public String spreadType() {
		return null;
	}

	@Override
	public Map<String, Object> idempotencyPayload() {
		if (reportType == com.myeongro.api.domain.saju.model.SajuReportType.ANNUAL_MONTHLY) {
			return ReadingInputSupport.orderedMap("reportType", reportType.value(), "question", question,
				"focusArea", focusArea.value(), "birthProfile", birthProfile, "targetYear", targetYear);
		}
		return ReadingInputSupport.orderedMap(
			"question", question,
			"focusArea", focusArea.value(),
			"birthProfile", birthProfile,
			"targetYear", targetYear
		);
	}
}
