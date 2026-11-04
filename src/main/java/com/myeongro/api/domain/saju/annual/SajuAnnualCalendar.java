package com.myeongro.api.domain.saju.annual;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.myeongro.api.domain.saju.calculation.SajuMonthlyCalendar;
import com.myeongro.api.domain.saju.calculation.SajuMonthlyCalendarService;

/** A deterministic view of the frozen M3 calendar, not a new fortune calculation. */
public record SajuAnnualCalendar(int targetYear, List<Month> months, List<Period> periods) {
	public SajuAnnualCalendar { months = List.copyOf(months); periods = List.copyOf(periods); }
	public record Month(int month, List<String> periodIds) {
		public Month { periodIds = List.copyOf(periodIds); }
	}
	public record Period(String id, String startInclusive, String endExclusive,
		String yearGanZhi, String monthGanZhi, String stemTenGod) { }

	public static SajuAnnualCalendar from(SajuMonthlyCalendar calendar) {
		if (calendar == null || !SajuMonthlyCalendarService.VERSION.equals(calendar.calculationVersion())
			|| !"Asia/Seoul".equals(calendar.timeZone()) || calendar.targetYear() < 1900
			|| calendar.targetYear() > 2100 || calendar.months().size() != 12) {
			throw new IllegalArgumentException("Invalid monthly calendar");
		}
		List<Month> months = new ArrayList<>();
		List<Period> periods = new ArrayList<>();
		for (int index = 0; index < 12; index++) {
			var month = calendar.months().get(index);
			if (month.month() != index + 1 || month.periods().size() != 2) {
				throw new IllegalArgumentException("Invalid calendar month");
			}
			OffsetDateTime cursor = java.time.LocalDate.of(calendar.targetYear(), index + 1, 1)
				.atStartOfDay().atOffset(ZoneOffset.ofHours(9));
			OffsetDateTime monthEnd = cursor.plusMonths(1);
			List<String> references = new ArrayList<>();
			for (var slice : month.periods()) {
				var start = OffsetDateTime.parse(slice.startInclusive());
				var end = OffsetDateTime.parse(slice.endExclusive());
				if (!start.equals(cursor) || !start.getOffset().equals(ZoneOffset.ofHours(9))
					|| !end.getOffset().equals(ZoneOffset.ofHours(9)) || !end.isAfter(start) || end.isAfter(monthEnd)
					|| slice.yearGanZhi() == null || slice.monthGanZhi() == null) {
					throw new IllegalArgumentException("Invalid monthly interval");
				}
				Period previous = periods.isEmpty() ? null : periods.getLast();
				String id;
				if (previous != null && OffsetDateTime.parse(previous.endExclusive()).equals(start)
					&& previous.yearGanZhi().equals(slice.yearGanZhi())
					&& previous.monthGanZhi().equals(slice.monthGanZhi())
					&& Objects.equals(previous.stemTenGod(), slice.stemTenGod())) {
					id = previous.id();
					periods.set(periods.size() - 1, new Period(id, previous.startInclusive(), slice.endExclusive(),
						slice.yearGanZhi(), slice.monthGanZhi(), slice.stemTenGod()));
				} else {
					id = "p%02d".formatted(periods.size() + 1);
					periods.add(new Period(id, slice.startInclusive(), slice.endExclusive(),
						slice.yearGanZhi(), slice.monthGanZhi(), slice.stemTenGod()));
				}
				references.add(id);
				cursor = end;
			}
			if (!cursor.equals(monthEnd)) throw new IllegalArgumentException("Incomplete month");
			months.add(new Month(index + 1, references));
		}
		return new SajuAnnualCalendar(calendar.targetYear(), months, periods);
	}
}
