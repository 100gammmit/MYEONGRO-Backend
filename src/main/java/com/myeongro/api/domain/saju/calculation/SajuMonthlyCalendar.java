package com.myeongro.api.domain.saju.calculation;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

public record SajuMonthlyCalendar(
	String calculationVersion, String timeZone, int targetYear, List<CalendarMonth> months
) {
	public SajuMonthlyCalendar { months = List.copyOf(months); }
	public record CalendarMonth(int month, List<Period> periods) {
		public CalendarMonth { periods = List.copyOf(periods); }
	}
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Period(
		String startInclusive, String endExclusive, String yearGanZhi,
		String monthGanZhi, String stemTenGod
	) { }
}
