package com.myeongro.api.domain.saju.calculation;

import static com.myeongro.api.domain.saju.calculation.UnknownTimeEquivalenceSupport.REFERENCE_LONGITUDE;
import static com.myeongro.api.domain.saju.calculation.UnknownTimeEquivalenceSupport.assertEquivalent;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.myeongro.api.domain.saju.model.LuckDirectionBasis;

class SegmentedUnknownTimeTests {

	private static final SajuCalculationSettings HOUR_23_NO_EQUATION = SajuCalculationSettings.precise(
		new SajuCalculationOptions(SajuCalculationOptions.DayBoundary.HOUR_23, false));

	/** Days where a pillar boundary, a DST gap/overlap or a standard-offset change falls inside the civil day. */
	static Stream<LocalDate> boundaryDates() {
		var transitions = ZoneId.of("Asia/Seoul").getRules().getTransitions().stream()
			.filter(transition -> List.of(1954, 1961, 1987).contains(transition.getDateTimeBefore().getYear()))
			.map(transition -> transition.getDateTimeBefore().toLocalDate());
		return Stream.concat(transitions, Stream.of(
			LocalDate.of(2020, 2, 4),   // 입춘 18:03 KST
			LocalDate.of(2020, 3, 5),   // 경칩 11:57 KST
			LocalDate.of(1900, 1, 1),
			LocalDate.of(2099, 12, 31)
		)).distinct();
	}

	@ParameterizedTest
	@MethodSource("boundaryDates")
	void legacyUnknownTimeMatchesTheMinuteByMinuteSearch(LocalDate date) {
		assertEquivalent(date, REFERENCE_LONGITUDE, SajuCalculationSettings.LEGACY);
	}

	@ParameterizedTest
	@MethodSource("boundaryDates")
	void publicOptionsMatchTheMinuteByMinuteSearch(LocalDate date) {
		assertEquivalent(date, REFERENCE_LONGITUDE, HOUR_23_NO_EQUATION);
	}

	@Test
	void extremeLongitudesMatchTheMinuteByMinuteSearch() {
		for (double longitude : new double[] {124.71, 130.87}) {
			assertEquivalent(LocalDate.of(2020, 2, 4), longitude, SajuCalculationSettings.LEGACY);
			assertEquivalent(LocalDate.of(1987, 10, 11), longitude, SajuCalculationSettings.precise(SajuCalculationOptions.DEFAULT));
		}
	}

	@Test
	void anUnknownDayNeedsOnlyAFewEngineCalls() {
		var calls = new AtomicInteger();
		var countingAdapter = new LunarJavaFourPillarsAdapter() {
			@Override
			public Candidate calculate(LocalDateTime trueSolarTime, LocalDateTime engineCivilTime, boolean includeTime,
				LuckDirectionBasis basis, int targetYear, SajuCalculationOptions options) {
				calls.incrementAndGet();
				return super.calculate(trueSolarTime, engineCivilTime, includeTime, basis, targetYear, options);
			}
		};
		var resolver = new ApproximateBirthTimeResolver(new TrueSolarTimeCorrector(), countingAdapter);
		for (LocalDate date : List.of(LocalDate.of(2020, 2, 4), LocalDate.of(1987, 10, 11), LocalDate.of(2000, 6, 15))) {
			calls.set(0);
			var resolution = resolver.resolveUnknown(date, REFERENCE_LONGITUDE, LuckDirectionBasis.MALE, 2026, () -> { },
				SajuCalculationSettings.LEGACY, SajuCandidateObserver.NONE);
			assertThat(calls.get()).as(date.toString()).isLessThanOrEqualTo(64);
			// The searched count still covers every minute (and both offsets of a DST overlap).
			assertThat(resolution.uncertainty().candidateCount()).isGreaterThanOrEqualTo(1380);
		}
	}
}
