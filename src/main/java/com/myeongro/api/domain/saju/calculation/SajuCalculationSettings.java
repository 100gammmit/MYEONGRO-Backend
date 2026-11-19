package com.myeongro.api.domain.saju.calculation;

/** Explicit per-request rules: never change a shared default to migrate legacy readings. */
public record SajuCalculationSettings(SajuCalculationOptions options, CorrectionModel correctionModel) {
	public enum CorrectionModel { LEGACY_DAY_OF_YEAR, MEEUS_NOAA_V1 }
	/**
	 * saju-ko-v4 rules (day-of-year equation of time). No production path uses it since saju-ko-v5; it remains for the
	 * no-settings overloads and the frozen v4 regression fixtures.
	 */
	public static final SajuCalculationSettings LEGACY = new SajuCalculationSettings(
		new SajuCalculationOptions(SajuCalculationOptions.DayBoundary.MIDNIGHT, true), CorrectionModel.LEGACY_DAY_OF_YEAR);
	public SajuCalculationSettings { java.util.Objects.requireNonNull(options); java.util.Objects.requireNonNull(correctionModel); }
	public static SajuCalculationSettings precise(SajuCalculationOptions options) {
		return new SajuCalculationSettings(options, CorrectionModel.MEEUS_NOAA_V1);
	}
}
