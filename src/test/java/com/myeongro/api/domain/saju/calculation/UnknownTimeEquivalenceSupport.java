package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.myeongro.api.domain.saju.model.LuckDirectionBasis;

/** Compares the segmented unknown-time search with the original minute-by-minute search. */
final class UnknownTimeEquivalenceSupport {

	static final double REFERENCE_LONGITUDE = SajuCalculationRules.KOREA_REFERENCE_LONGITUDE;
	static final List<SajuCalculationSettings> ALL_SETTINGS = List.of(
		SajuCalculationSettings.LEGACY,
		SajuCalculationSettings.precise(SajuCalculationOptions.DEFAULT),
		SajuCalculationSettings.precise(new SajuCalculationOptions(SajuCalculationOptions.DayBoundary.MIDNIGHT, false)),
		SajuCalculationSettings.precise(new SajuCalculationOptions(SajuCalculationOptions.DayBoundary.HOUR_23, true)),
		SajuCalculationSettings.precise(new SajuCalculationOptions(SajuCalculationOptions.DayBoundary.HOUR_23, false))
	);

	private static final TrueSolarTimeCorrector CORRECTOR = new TrueSolarTimeCorrector();
	private static final LunarJavaFourPillarsAdapter ADAPTER = new LunarJavaFourPillarsAdapter();
	static final ApproximateBirthTimeResolver SEGMENTED = new ApproximateBirthTimeResolver(CORRECTOR, ADAPTER, true);
	static final ApproximateBirthTimeResolver EXHAUSTIVE = new ApproximateBirthTimeResolver(CORRECTOR, ADAPTER, false);

	private UnknownTimeEquivalenceSupport() {
	}

	static void assertEquivalent(LocalDate date, double longitude, SajuCalculationSettings settings) {
		Outcome expected = run(EXHAUSTIVE, date, longitude, settings);
		Outcome actual = run(SEGMENTED, date, longitude, settings);
		String label = date + " lon=" + longitude + " " + settings;
		assertThat(actual.resolution()).as(label).isEqualTo(expected.resolution());
		assertThat(actual.observed()).as(label + " observer").isEqualTo(expected.observed());
	}

	static Outcome run(ApproximateBirthTimeResolver resolver, LocalDate date, double longitude,
		SajuCalculationSettings settings) {
		var accumulator = new ManseryeokChartBuilder().accumulator();
		var resolution = resolver.resolveUnknown(date, longitude, LuckDirectionBasis.MALE, 2026, () -> { },
			settings, accumulator);
		return new Outcome(resolution, observed(accumulator));
	}

	/** Every public accumulator output, with varying fields kept in insertion order. */
	private static List<Object> observed(ManseryeokChartBuilder.Accumulator accumulator) {
		List<Object> values = new ArrayList<>();
		values.add(accumulator.dayMaster());
		values.add(accumulator.integerLuck());
		values.add(accumulator.hiddenStems());
		values.add(accumulator.twelveStages());
		values.add(accumulator.voids());
		values.add(accumulator.relations());
		values.add(List.copyOf(accumulator.varyingFields()));
		return values;
	}

	record Outcome(ApproximateBirthTimeResolver.Resolution resolution, List<Object> observed) {
	}
}
