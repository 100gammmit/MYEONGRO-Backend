package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.saju.controller.SajuCalculationRequest;
import com.myeongro.api.domain.saju.service.ManseryeokCalculationService;

/** M3-A: reference year selection and validation before a calculation slot is taken. */
class ManseryeokPublicAccessTests {

	private static final String PROFILE = "{\"calendarType\":\"solar\",\"birthDate\":\"2000-06-15\",\"birthTime\":\"14:30\","
		+ "\"birthTimePrecision\":\"exact\",\"provinceCode\":\"11\",\"luckDirectionBasis\":\"male\"}";

	private static SajuCalculationRequest request(String referenceYear) throws Exception {
		return ManseryeokCalculationTests.MAPPER.readValue("{\"birthProfile\":" + PROFILE
			+ (referenceYear == null ? "" : ",\"referenceYear\":" + referenceYear) + "}", SajuCalculationRequest.class);
	}

	@Test
	void referenceYearIsThisYearByDefaultAndMayBeNextYear() throws Exception {
		var service = ManseryeokCalculationTests.service(); // clock fixed in 2026 (Seoul)
		for (String[] test : new String[][] {{null, "2026"}, {"2026", "2026"}, {"2027", "2027"}}) {
			int year = Integer.parseInt(test[1]);
			var chart = service.calculate(request(test[0]));
			assertThat(chart.referenceYear()).as(String.valueOf(test[0])).isEqualTo(year);
			assertThat(chart.annualFlows().getFirst().year()).isEqualTo(year);
			assertThat(chart.monthlyCalendar().targetYear()).isEqualTo(year);
		}
		var thisYear = service.calculate(request(null));
		var nextYear = service.calculate(request("2027"));
		assertThat(nextYear.chart()).isEqualTo(thisYear.chart()); // the natal chart does not depend on the year
		assertThat(nextYear.calculationFingerprint()).isNotEqualTo(thisYear.calculationFingerprint());
	}

	@Test
	void majorLuckPeriodsCarryHanjaAndTheDayMasterStemTenGod() throws Exception {
		var chart = ManseryeokCalculationTests.service().calculate(request(null));
		var luck = chart.luckCycle();
		var precise = luck.precise().periods();
		assertThat(luck.periods()).hasSize(precise.size()).isNotEmpty();
		String dayMaster = chart.chart().dayMaster().hanja();
		for (int i = 0; i < precise.size(); i++) {
			var flow = luck.periods().get(i);
			var raw = precise.get(i);
			assertThat(List.of(flow.startYear(), flow.endYear(), flow.startAge(), flow.endAge()))
				.isEqualTo(List.of(raw.startYear(), raw.endYear(), raw.startAge(), raw.endAge()));
			assertThat(flow.ganZhi().korean()).isEqualTo(raw.ganZhi());
			assertThat(SajuKoreanTerms.ganZhi(flow.ganZhi().hanja())).isEqualTo(raw.ganZhi());
			assertThat(flow.stemTenGod()).as(flow.ganZhi().hanja())
				.isEqualTo(tenGod(dayMaster, flow.ganZhi().hanja().substring(0, 1)));
		}
		// Same rule as the annual flows: equal stems give equal ten gods.
		for (var annual : chart.annualFlows()) {
			assertThat(annual.stemTenGod()).isEqualTo(tenGod(dayMaster, annual.ganZhi().hanja().substring(0, 1)));
		}
	}

	@Test
	void ganZhiHanjaReversesEverySexagenaryPair() {
		String stems = "甲乙丙丁戊己庚辛壬癸", branches = "子丑寅卯辰巳午未申酉戌亥";
		for (int i = 0; i < 60; i++) {
			String hanja = "" + stems.charAt(i % 10) + branches.charAt(i % 12);
			assertThat(SajuKoreanTerms.ganZhiHanja(SajuKoreanTerms.ganZhi(hanja))).isEqualTo(hanja);
		}
		for (String bad : new String[] {"갑", "갑자자", "가자", "자갑", ""}) {
			assertThatThrownBy(() -> SajuKoreanTerms.ganZhiHanja(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
		}
	}

	/** Independent of lunar-java: five-element cycle and yin-yang of the two stems. */
	private static String tenGod(String dayMaster, String stem) {
		String stems = "甲乙丙丁戊己庚辛壬癸";
		int day = stems.indexOf(dayMaster), other = stems.indexOf(stem);
		int relation = Math.floorMod(other / 2 - day / 2, 5);
		boolean samePolarity = day % 2 == other % 2;
		String[][] names = {{"비견", "겁재"}, {"식신", "상관"}, {"편재", "정재"}, {"편관", "정관"}, {"편인", "정인"}};
		return names[relation][samePolarity ? 0 : 1];
	}

	@Test
	void rejectsAnyOtherReferenceYear() throws Exception {
		var service = ManseryeokCalculationTests.service();
		for (String value : new String[] {"2025", "2028", "\"2027\"", "2027.5", "null", "99999999999", "[2027]"}) {
			var request = request(value);
			assertThatThrownBy(() -> service.calculate(request)).as(value)
				.isInstanceOfSatisfying(InvalidReadingRequestException.class, e -> {
					assertThat(e.getCode()).isEqualTo("INVALID_REFERENCE_YEAR");
					assertThat(e.getField()).isEqualTo("referenceYear");
				});
		}
	}

	@Test
	void invalidInputIsRejectedWithoutTakingACalculationSlot() throws Exception {
		var service = ManseryeokCalculationTests.service();
		var field = ManseryeokCalculationService.class.getDeclaredField("slots");
		field.setAccessible(true);
		var slots = (Semaphore)field.get(service);
		int drained = slots.drainPermits(); // every slot busy
		try {
			var badYear = request("2030");
			assertThatThrownBy(() -> service.calculate(badYear)).isInstanceOf(InvalidReadingRequestException.class);
			var badProfile = ManseryeokCalculationTests.MAPPER.readValue(
				"{\"birthProfile\":" + PROFILE.replace("\"11\"", "\"99\"") + "}", SajuCalculationRequest.class);
			assertThatThrownBy(() -> service.calculate(badProfile)).isInstanceOf(InvalidReadingRequestException.class);
			var valid = request(null);
			assertThatThrownBy(() -> service.calculate(valid)).isInstanceOfSatisfying(
				ManseryeokCalculationService.Unavailable.class, e -> assertThat(e.code()).isEqualTo("SAJU_CALCULATION_BUSY"));
		} finally {
			slots.release(drained);
		}
	}
}
