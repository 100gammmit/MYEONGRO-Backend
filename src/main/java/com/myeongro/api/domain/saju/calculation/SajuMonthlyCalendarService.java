package com.myeongro.api.domain.saju.calculation;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import com.nlf.calendar.util.LunarUtil;

@Component
public class SajuMonthlyCalendarService {
	public static final String VERSION = "saju-monthly-ko-v1";
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

	// Run once after birth-time candidate merging, never once per candidate.
	// lunar-java's term clock is UTC+8; public periods are Korea civil time.
	public SajuMonthlyCalendar calculate(int targetYear, String dayMaster) {
		try {
			return doCalculate(targetYear, dayMaster);
		} catch (RuntimeException exception) {
			throw new SajuCalculationException(exception);
		}
	}

	private SajuMonthlyCalendar doCalculate(int targetYear, String dayMaster) {
		if (targetYear < 1900 || targetYear > 2100) throw new IllegalArgumentException("Unsupported flow year");
		String dayStem = engineStem(dayMaster);
		var table = Solar.fromYmd(targetYear, 7, 1).getLunar().getJieQiTable();
		var terms = new TreeSet<OffsetDateTime>();
		for (int index = 0; index < Lunar.JIE_QI_IN_USE.length; index += 2) {
			Solar term = table.get(Lunar.JIE_QI_IN_USE[index]);
			OffsetDateTime korean = LocalDateTime.parse(term.toYmdHms().replace(' ', 'T'))
				.atOffset(SajuCalculationRules.ENGINE_OFFSET)
				.atZoneSameInstant(SajuCalculationRules.BIRTH_ZONE).toOffsetDateTime();
			if (korean.getYear() == targetYear) terms.add(korean);
		}
		if (terms.size() != 12) throw new IllegalStateException("Expected twelve monthly solar terms");
		List<SajuMonthlyCalendar.CalendarMonth> months = new ArrayList<>();
		for (int month = 1; month <= 12; month++) {
			YearMonth calendarMonth = YearMonth.of(targetYear, month);
			OffsetDateTime start = calendarMonth.atDay(1).atStartOfDay(SajuCalculationRules.BIRTH_ZONE).toOffsetDateTime();
			OffsetDateTime end = calendarMonth.plusMonths(1).atDay(1).atStartOfDay(SajuCalculationRules.BIRTH_ZONE).toOffsetDateTime();
			List<OffsetDateTime> cuts = new ArrayList<>();
			cuts.add(start);
			terms.stream().filter(term -> term.isAfter(start) && term.isBefore(end)).forEach(cuts::add);
			cuts.add(end);
			List<SajuMonthlyCalendar.Period> periods = new ArrayList<>();
			for (int index = 0; index < cuts.size() - 1; index++) {
				OffsetDateTime from = cuts.get(index);
				LocalDateTime engineTime = from.withOffsetSameInstant(SajuCalculationRules.ENGINE_OFFSET).toLocalDateTime();
				Lunar lunar = Solar.fromYmdHms(engineTime.getYear(), engineTime.getMonthValue(), engineTime.getDayOfMonth(),
					engineTime.getHour(), engineTime.getMinute(), engineTime.getSecond()).getLunar();
				String monthGanZhi = lunar.getMonthInGanZhiExact();
				String tenGod = dayStem == null ? null
					: SajuKoreanTerms.tenGod(LunarUtil.SHI_SHEN.get(dayStem + monthGanZhi.substring(0, 1)));
				periods.add(new SajuMonthlyCalendar.Period(from.format(TIMESTAMP), cuts.get(index + 1).format(TIMESTAMP),
					SajuKoreanTerms.ganZhi(lunar.getYearInGanZhiExact()), SajuKoreanTerms.ganZhi(monthGanZhi), tenGod));
			}
			months.add(new SajuMonthlyCalendar.CalendarMonth(month, periods));
		}
		return new SajuMonthlyCalendar(VERSION, "Asia/Seoul", targetYear, months);
	}

	private String engineStem(String dayMaster) {
		if (dayMaster == null) return null;
		for (String stem : "甲乙丙丁戊己庚辛壬癸".split("")) {
			if (stem.equals(dayMaster) || SajuKoreanTerms.stem(stem).equals(dayMaster)) return stem;
		}
		throw new IllegalArgumentException("Unknown day master");
	}
}
