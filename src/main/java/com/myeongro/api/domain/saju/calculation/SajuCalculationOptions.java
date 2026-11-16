package com.myeongro.api.domain.saju.calculation;

public record SajuCalculationOptions(DayBoundary dayBoundary, boolean equationOfTime) {
	public enum DayBoundary {
		MIDNIGHT(2), HOUR_23(1);
		private final int sect;
		DayBoundary(int sect) { this.sect = sect; }
		public int sect() { return sect; }
	}
	public static final SajuCalculationOptions DEFAULT = new SajuCalculationOptions(DayBoundary.MIDNIGHT, true);
	public SajuCalculationOptions { java.util.Objects.requireNonNull(dayBoundary); }
}
