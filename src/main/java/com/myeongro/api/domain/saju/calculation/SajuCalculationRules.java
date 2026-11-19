package com.myeongro.api.domain.saju.calculation;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

public final class SajuCalculationRules {

	/** v5: precise (NOAA/Meeus) equation of time, shared with the public manseryeok. v4 used the day-of-year formula. */
	public static final String CALCULATION_VERSION = "saju-ko-v5";
	public static final String LEGACY_DAY_OF_YEAR_VERSION = "saju-ko-v4";
	private static final Set<String> SUPPORTED_VERSIONS = Set.of(
		"saju-ko-v1", "saju-ko-v2", "saju-ko-v3", LEGACY_DAY_OF_YEAR_VERSION, CALCULATION_VERSION
	);
	public static final String ENGINE = "lunar-java";
	public static final String ENGINE_VERSION = "1.7.7";
	public static final ZoneId BIRTH_ZONE = ZoneId.of("Asia/Seoul");
	public static final ZoneOffset ENGINE_OFFSET = ZoneOffset.ofHours(8);
	public static final int DAY_BOUNDARY_SECT = 2;
	public static final int LUCK_START_SECT = 2;
	public static final int APPROXIMATE_MINUTES = 60;
	public static final double KOREA_REFERENCE_LONGITUDE = 127.5d;

	private SajuCalculationRules() {
	}

	public static boolean supports(String version) {
		return SUPPORTED_VERSIONS.contains(version);
	}
}
