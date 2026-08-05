package com.myeongro.api.domain.saju.calculation;

import java.time.ZoneId;

public final class SajuCalculationRules {

	public static final String CALCULATION_VERSION = "saju-ko-v1";
	public static final String ENGINE = "lunar-java";
	public static final String ENGINE_VERSION = "1.7.7";
	public static final ZoneId BIRTH_ZONE = ZoneId.of("Asia/Seoul");
	public static final int DAY_BOUNDARY_SECT = 2;
	public static final int LUCK_START_SECT = 2;
	public static final int APPROXIMATE_MINUTES = 60;

	private SajuCalculationRules() {
	}
}
