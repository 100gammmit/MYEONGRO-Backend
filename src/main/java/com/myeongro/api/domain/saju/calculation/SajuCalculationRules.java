package com.myeongro.api.domain.saju.calculation;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

public final class SajuCalculationRules {

	public static final String CALCULATION_VERSION = "saju-ko-v2";
	private static final Set<String> SUPPORTED_VERSIONS = Set.of(
		"saju-ko-v1", CALCULATION_VERSION
	);
	public static final String ENGINE = "lunar-java";
	public static final String ENGINE_VERSION = "1.7.7";
	public static final ZoneId BIRTH_ZONE = ZoneId.of("Asia/Seoul");
	public static final ZoneOffset ENGINE_OFFSET = ZoneOffset.ofHours(8);
	public static final int DAY_BOUNDARY_SECT = 2;
	public static final int LUCK_START_SECT = 2;
	public static final int APPROXIMATE_MINUTES = 60;

	private SajuCalculationRules() {
	}

	public static boolean supports(String version) {
		return SUPPORTED_VERSIONS.contains(version);
	}
}
