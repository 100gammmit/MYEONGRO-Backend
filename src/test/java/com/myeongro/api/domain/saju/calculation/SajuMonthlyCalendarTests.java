package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import com.nlf.calendar.Solar;

class SajuMonthlyCalendarTests {
	private final SajuMonthlyCalendarService service = new SajuMonthlyCalendarService();
	@Test void keepsTheExistingCalculationFailureContract() {
		assertThatThrownBy(() -> service.calculate(2027, "unsupported"))
			.isInstanceOf(SajuCalculationException.class)
			.extracting("code").isEqualTo("SAJU_CALCULATION_FAILED");
	}
	@Test void matchesFrozenMonthlyV1CutsAndGanZhi() throws Exception {
		var fixture = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
			new org.springframework.core.io.ClassPathResource("saju/golden/saju-monthly-ko-v1.json").getInputStream());
		var calendar = service.calculate(fixture.get("targetYear").asInt(), "임");
		assertThat(calendar.calculationVersion()).isEqualTo(fixture.get("calculationVersion").asText());
		for (int month = 0; month < 12; month++) {
			var periods = calendar.months().get(month).periods();
			assertThat(periods.getLast().startInclusive()).isEqualTo(fixture.get("cutsKst").get(month).asText());
			assertThat(periods.getLast().monthGanZhi()).isEqualTo(fixture.get("afterMonthGanZhi").get(month).asText());
		}
		assertThat(calendar.months().getFirst().periods().getFirst().monthGanZhi()).isEqualTo("경자");
	}

	@Test void coversTwelveCalendarMonthsWithoutGapsAndSwitchesExactlyAtTerms() {
		for (int year : new int[]{2020, 2024, 2026, 2027, 2099}) {
			var calendar = service.calculate(year, "임");
			assertThat(calendar.months()).hasSize(12);
			String previousEnd = null;
			for (var month : calendar.months()) {
				assertThat(month.periods()).hasSize(2);
				for (var period : month.periods()) {
					assertThat(OffsetDateTime.parse(period.startInclusive())).isBefore(OffsetDateTime.parse(period.endExclusive()));
					if (previousEnd != null) assertThat(period.startInclusive()).isEqualTo(previousEnd);
					previousEnd = period.endExclusive();
					assertThat(period.monthGanZhi()).isEqualTo(monthAt(period.startInclusive()));
					assertThat(period.monthGanZhi()).isEqualTo(monthAt(OffsetDateTime.parse(period.endExclusive()).minusSeconds(1).toString()));
					assertThat(period.stemTenGod()).isNotBlank();
				}
				assertThat(month.periods().getFirst().monthGanZhi()).isNotEqualTo(month.periods().getLast().monthGanZhi());
			}
			assertThat(calendar.months().getFirst().periods().getFirst().startInclusive()).isEqualTo(year + "-01-01T00:00:00+09:00");
			assertThat(previousEnd).isEqualTo((year + 1) + "-01-01T00:00:00+09:00");
		}
	}

	@Test void freezesKoreanLichunBoundaryRatherThanTrueSolarOrEngineClock() {
		var february = service.calculate(2020, "임").months().get(1).periods();
		assertThat(february.getFirst().endExclusive()).isEqualTo("2020-02-04T18:03:19+09:00");
		assertThat(february.getFirst().yearGanZhi()).isEqualTo("기해");
		assertThat(february.getFirst().monthGanZhi()).isEqualTo("정축");
		assertThat(february.getLast().yearGanZhi()).isEqualTo("경자");
		assertThat(february.getLast().monthGanZhi()).isEqualTo("무인");
	}

	@Test void omitsPersonalTenGodsWhenMergedDayMasterIsUncertain() {
		var unknown = service.calculate(2026, null);
		assertThat(unknown.months()).allSatisfy(month -> assertThat(month.periods()).allSatisfy(period ->
			assertThat(period.stemTenGod()).isNull()));
		assertThat(service.calculate(2026, "壬")).isEqualTo(service.calculate(2026, "임"));
	}

	private String monthAt(String koreanTime) {
		LocalDateTime engine = OffsetDateTime.parse(koreanTime).withOffsetSameInstant(SajuCalculationRules.ENGINE_OFFSET).toLocalDateTime();
		return SajuKoreanTerms.ganZhi(Solar.fromYmdHms(engine.getYear(), engine.getMonthValue(), engine.getDayOfMonth(),
			engine.getHour(), engine.getMinute(), engine.getSecond()).getLunar().getMonthInGanZhiExact());
	}
}
