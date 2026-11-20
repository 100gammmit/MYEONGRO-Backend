package com.myeongro.api.domain.saju.calculation;

import static com.myeongro.api.domain.saju.calculation.UnknownTimeEquivalenceSupport.ALL_SETTINGS;
import static com.myeongro.api.domain.saju.calculation.UnknownTimeEquivalenceSupport.REFERENCE_LONGITUDE;
import static com.myeongro.api.domain.saju.calculation.UnknownTimeEquivalenceSupport.assertEquivalent;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.nlf.calendar.Solar;

/**
 * Wide equivalence sweep of the segmented unknown-time search. Each exhaustive reference day costs seconds, so this
 * runs only through {@code ./gradlew slowTest}.
 */
@Tag("slow")
class SegmentedUnknownTimeSlowTests {

	private static final long SEED = 20261118L;
	private static final int RANDOM_DATES = 120;

	@Test
	void everySettingMatchesTheMinuteByMinuteSearchAcrossBoundaryAndRandomDays() throws Exception {
		List<Case> cases = new ArrayList<>();
		for (LocalDate date : dates()) {
			for (SajuCalculationSettings settings : ALL_SETTINGS) {
				cases.add(new Case(date, REFERENCE_LONGITUDE, settings));
			}
		}
		for (LocalDate date : transitionDates()) {
			for (double longitude : new double[] {124.71, 130.87}) {
				cases.add(new Case(date, longitude, SajuCalculationSettings.LEGACY));
				cases.add(new Case(date, longitude, SajuCalculationSettings.precise(SajuCalculationOptions.DEFAULT)));
			}
		}
		var pool = new ForkJoinPool(Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() - 1)));
		try {
			pool.submit(() -> cases.parallelStream()
				.forEach(test -> assertEquivalent(test.date(), test.longitude(), test.settings()))).get();
		} finally {
			pool.shutdown();
		}
		System.out.printf("SEGMENTED_UNKNOWN_EQUIVALENCE cases=%d dates=%d seed=%d%n", cases.size(), dates().size(), SEED);
	}

	private static Set<LocalDate> dates() {
		Set<LocalDate> dates = new LinkedHashSet<>(transitionDates());
		// Every 절(jie) day of two years, plus the days on either side of 입춘.
		for (int year : new int[] {1985, 2020}) {
			Solar.fromYmd(year, 6, 1).getLunar().getJieQiTable().forEach((name, solar) -> {
				LocalDate day = LocalDate.of(solar.getYear(), solar.getMonth(), solar.getDay());
				if (day.getYear() == year) dates.add(day);
			});
			dates.add(LocalDate.of(year, 2, 3));
			dates.add(LocalDate.of(year, 2, 5));
		}
		Random random = new Random(SEED);
		long first = LocalDate.of(1900, 1, 1).toEpochDay();
		long last = LocalDate.of(2099, 12, 31).toEpochDay();
		int added = 0;
		while (added < RANDOM_DATES) {
			if (dates.add(LocalDate.ofEpochDay(first + (long)(random.nextDouble() * (last - first + 1))))) added++;
		}
		return dates;
	}

	/** Civil days of every Asia/Seoul offset transition from 1900 to 2099. */
	private static Set<LocalDate> transitionDates() {
		Set<LocalDate> dates = new LinkedHashSet<>();
		ZoneId.of("Asia/Seoul").getRules().getTransitions().forEach(transition -> {
			int year = transition.getDateTimeBefore().getYear();
			if (year >= 1900 && year <= 2099) {
				dates.add(transition.getDateTimeBefore().toLocalDate());
				dates.add(transition.getDateTimeAfter().toLocalDate());
			}
		});
		return dates;
	}

	private record Case(LocalDate date, double longitude, SajuCalculationSettings settings) {
	}
}
