package com.myeongro.api.domain.saju.annual;

import java.util.Map;

/**
 * Which annual report a calculation gets. It is full only when every birth-time candidate agrees on the day master;
 * otherwise the brief report is served and priced. The format check, the generator and the charge all use this rule.
 */
public final class SajuAnnualDetailLevel {

	public static final String FULL = "full";
	public static final String BRIEF = "brief";

	private SajuAnnualDetailLevel() {
	}

	public static String of(String dayMaster) {
		return dayMaster != null && !dayMaster.isBlank() ? FULL : BRIEF;
	}

	/** For a stored or model calculation map, which carries {@code dayMaster} only when it is certain. */
	public static String ofCalculation(Map<?, ?> calculation) {
		return of(calculation.get("dayMaster") instanceof String dayMaster ? dayMaster : null);
	}
}
