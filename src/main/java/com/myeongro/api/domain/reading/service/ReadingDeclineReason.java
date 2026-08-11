package com.myeongro.api.domain.reading.service;

import java.util.Arrays;
import java.util.List;

public enum ReadingDeclineReason {

	CRISIS_OR_IMMEDIATE_DANGER,
	MEDICAL_DECISION,
	LEGAL_DECISION,
	FINANCIAL_DECISION,
	HARMFUL_OR_ILLEGAL_ACTION;

	public static List<String> modelSelectableValues() {
		return List.of(
			CRISIS_OR_IMMEDIATE_DANGER.name(),
			HARMFUL_OR_ILLEGAL_ACTION.name()
		);
	}

	public static ReadingDeclineReason fromValue(String value) {
		return Arrays.stream(values())
			.filter(reason -> reason.name().equals(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unsupported reading decline reason"));
	}
}
