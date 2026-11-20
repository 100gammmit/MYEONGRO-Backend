package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
