package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.saju.service.ManseryeokCalculationService;

/**
 * Since saju-ko-v5 an AI reading and the public manseryeok must produce the same chart for the same input with the
 * default options and province longitude.
 */
class AiManseryeokParityTests {

	private static final int TARGET_YEAR = 2026; // ManseryeokCalculationTests.service() fixes its clock in 2026.

	@Test
	void theAiReadingPathMatchesThePublicManseryeokOnEveryExternalSampleAndV5BoundaryCase() throws Exception {
		var catalog = ManseryeokCalculationTests.CATALOG;
		var corrector = new TrueSolarTimeCorrector();
		var ai = new SajuCalculationService(catalog, corrector,
			new ApproximateBirthTimeResolver(corrector, new LunarJavaFourPillarsAdapter()));
		ManseryeokCalculationService manseryeok = ManseryeokCalculationTests.service();

		List<String[]> samples = new ArrayList<>(externalSamples());
		// saju-ko-v5.json cases where the precise equation of time moved the hour or day pillar away from v4.
		samples.add(new String[] {"2015-10-25", "13:16", "exact"});
		samples.add(new String[] {"2015-10-25", "00:16", "exact"});
		samples.add(new String[] {"1995-11-08", "09:16", "exact"});
		samples.add(new String[] {"1995-11-08", "00:16", "exact"});

		int compared = 0;
		int rejected = 0;
		// Every luck basis (forward, backward, none) and a second province longitude (Busan) besides Seoul.
		List<String[]> variants = List.of(new String[] {"male", "11"}, new String[] {"female", "11"},
			new String[] {"unspecified", "11"}, new String[] {"female", "26"});
		for (String[] sample : samples) {
		for (String[] variant : variants) {
			String date = sample[0];
			String time = sample[1].isEmpty() ? null : sample[1];
			String precision = sample[2];
			String label = String.join(" ", sample) + " " + String.join("/", variant);
			Map<String, Object> profile = new LinkedHashMap<>();
			profile.put("calendarType", "solar");
			profile.put("birthDate", date);
			profile.put("birthTimePrecision", precision);
			profile.put("luckDirectionBasis", variant[0]);
			if (time != null) {
				profile.put("birthTime", time);
				profile.put("provinceCode", variant[1]);
			}
			var request = ManseryeokCalculationTests.MAPPER.convertValue(
				Map.of("birthProfile", profile), com.myeongro.api.domain.saju.controller.SajuCalculationRequest.class);

			SajuCalculationSnapshot aiChart;
			try {
				aiChart = ai.calculate(profile, TARGET_YEAR);
			} catch (InvalidReadingRequestException aiRejection) {
				assertThatThrownBy(() -> manseryeok.calculate(request)).as(label)
					.isInstanceOfSatisfying(InvalidReadingRequestException.class,
						e -> assertThat(e.getCode()).isEqualTo(aiRejection.getCode()));
				rejected++;
				continue;
			}
			var publicChart = manseryeok.calculate(request);

			assertThat(aiChart.calculationVersion()).isEqualTo(SajuCalculationRules.CALCULATION_VERSION);
			for (String position : List.of("year", "month", "day", "time")) {
				var aiPillar = pillar(aiChart.pillars(), position);
				var publicPillar = publicChart.chart().pillars().get(position);
				assertThat(aiPillar == null ? null : aiPillar.ganZhi()).as(label + " " + position)
					.isEqualTo(publicPillar == null ? null : publicPillar.ganZhi().korean());
			}
			var publicDayMaster = publicChart.chart().dayMaster();
			assertThat(aiChart.dayMaster()).as(label + " dayMaster")
				.isEqualTo(publicDayMaster == null ? null : publicDayMaster.korean());
			assertThat(aiChart.fiveElements()).as(label + " fiveElements").isEqualTo(publicChart.fiveElements());
			assertThat(aiChart.luckCycle()).as(label + " luckCycle")
				.isEqualTo(publicChart.luckCycle() == null ? null : publicChart.luckCycle().precise());
			assertThat(aiChart.limitations()).as(label + " limitations").isEqualTo(publicChart.limitations());
			compared++;
		}
		}
		// Only the DST-gap sample is rejected, by both paths, in every variant.
		assertThat(rejected).isEqualTo(variants.size());
		assertThat(compared).isEqualTo((samples.size() - 1) * variants.size());
	}

	/** B2: a city longitude and non-default options give the same chart on the AI path as on the public one. */
	@Test
	void cityLongitudeAndOptionsMatchThePublicManseryeok() throws Exception {
		var catalog = ManseryeokCalculationTests.CATALOG;
		var corrector = new TrueSolarTimeCorrector();
		var ai = new SajuCalculationService(catalog, corrector,
			new ApproximateBirthTimeResolver(corrector, new LunarJavaFourPillarsAdapter()));
		ManseryeokCalculationService manseryeok = ManseryeokCalculationTests.service();
		// date, time, precision, province, city (Ulleung, Ongjin incl. Baengnyeong, Jongno), boundary, equation
		String[][] cases = {
			{"1995-11-08", "00:16", "exact", "47", "47940", "HOUR_23", "false"},
			{"1990-01-01", "23:40", "exact", "28", "28720", "HOUR_23", "true"},
			{"1990-01-01", "23:40", "exact", "28", "28720", "MIDNIGHT", "true"},
			{"2015-10-25", "13:16", "approximate", "11", "11110", "MIDNIGHT", "false"},
			{"2020-02-04", "", "unknown", "", "", "HOUR_23", "false"},
		};
		for (String[] test : cases) {
			Map<String, Object> profile = new LinkedHashMap<>();
			profile.put("calendarType", "solar");
			profile.put("birthDate", test[0]);
			profile.put("birthTimePrecision", test[2]);
			profile.put("luckDirectionBasis", "female");
			if (!test[1].isEmpty()) {
				profile.put("birthTime", test[1]);
				profile.put("provinceCode", test[3]);
				profile.put("cityCode", test[4]);
			}
			var request = ManseryeokCalculationTests.MAPPER.convertValue(Map.of("birthProfile", profile,
				"options", Map.of("dayBoundary", test[5], "equationOfTime", Boolean.parseBoolean(test[6]))),
				com.myeongro.api.domain.saju.controller.SajuCalculationRequest.class);
			Map<String, Object> normalized = new LinkedHashMap<>(profile);
			normalized.put("dayBoundary", test[5]);
			normalized.put("equationOfTime", Boolean.parseBoolean(test[6]));

			var aiChart = ai.calculate(normalized, TARGET_YEAR);
			var publicChart = manseryeok.calculate(request);
			String label = String.join(" ", test);
			assertThat(publicChart.appliedOptions().coordinateSource()).as(label)
				.isEqualTo(test[1].isEmpty() ? "reference" : "city");
			for (String position : List.of("year", "month", "day", "time")) {
				var aiPillar = pillar(aiChart.pillars(), position);
				var publicPillar = publicChart.chart().pillars().get(position);
				assertThat(aiPillar == null ? null : aiPillar.ganZhi()).as(label + " " + position)
					.isEqualTo(publicPillar == null ? null : publicPillar.ganZhi().korean());
			}
			assertThat(aiChart.timeCorrection()).as(label + " correction").isEqualTo(publicChart.timeCorrection());
			assertThat(aiChart.luckCycle()).as(label + " luckCycle")
				.isEqualTo(publicChart.luckCycle() == null ? null : publicChart.luckCycle().precise());
			assertThat(aiChart.limitations()).as(label + " limitations").isEqualTo(publicChart.limitations());
		}
	}

	/** The 23-o'clock day boundary moves a 23:xx birth to the next day's pillar. */
	@Test
	void hour23BoundaryMovesTheDayPillarOfALateBirth() {
		var catalog = ManseryeokCalculationTests.CATALOG;
		var corrector = new TrueSolarTimeCorrector();
		var ai = new SajuCalculationService(catalog, corrector,
			new ApproximateBirthTimeResolver(corrector, new LunarJavaFourPillarsAdapter()));
		Map<String, Object> profile = new LinkedHashMap<>(Map.of("calendarType", "solar", "birthDate", "1990-01-01",
			"birthTime", "23:59", "birthTimePrecision", "exact", "provinceCode", "11", "luckDirectionBasis", "unspecified"));
		var midnight = ai.calculate(profile, TARGET_YEAR);
		profile.put("dayBoundary", "HOUR_23");
		var hour23 = ai.calculate(profile, TARGET_YEAR);

		assertThat(midnight.pillars().day().ganZhi()).isEqualTo("병인");
		assertThat(hour23.pillars().day().ganZhi()).isEqualTo("정묘");
		assertThat(hour23.pillars().time().ganZhi()).isEqualTo(midnight.pillars().time().ganZhi());
	}

	private static SajuCalculationSnapshot.Pillar pillar(SajuCalculationSnapshot.Pillars pillars, String position) {
		return switch (position) {
			case "year" -> pillars.year();
			case "month" -> pillars.month();
			case "day" -> pillars.day();
			default -> pillars.time();
		};
	}

	/** date, time, precision of the 30 synthetic external UI samples. */
	private static List<String[]> externalSamples() throws Exception {
		try (var reader = new BufferedReader(new InputStreamReader(
			new ClassPathResource("saju/manseryeok/external-charts.csv").getInputStream(), StandardCharsets.UTF_8))) {
			List<String[]> samples = reader.lines()
				.filter(line -> !line.isBlank() && !line.startsWith("#"))
				.map(line -> line.split(",", -1))
				.map(fields -> new String[] {fields[1], fields[2], fields[3]})
				.toList();
			assertThat(samples).hasSize(30);
			return samples;
		}
	}
}
