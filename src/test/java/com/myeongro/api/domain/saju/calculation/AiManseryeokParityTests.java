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
		for (String[] sample : samples) {
			String date = sample[0];
			String time = sample[1].isEmpty() ? null : sample[1];
			String precision = sample[2];
			String label = String.join(" ", sample);
			Map<String, Object> profile = new LinkedHashMap<>();
			profile.put("calendarType", "solar");
			profile.put("birthDate", date);
			profile.put("birthTimePrecision", precision);
			profile.put("luckDirectionBasis", "male");
			if (time != null) {
				profile.put("birthTime", time);
				profile.put("provinceCode", "11");
			}
			var request = ManseryeokCalculationTests.request(date, time, precision, null);

			SajuCalculationSnapshot aiChart;
			try {
				aiChart = ai.calculate(profile, TARGET_YEAR);
			} catch (InvalidReadingRequestException rejected) {
				assertThatThrownBy(() -> manseryeok.calculate(request)).as(label)
					.isInstanceOfSatisfying(InvalidReadingRequestException.class,
						e -> assertThat(e.getCode()).isEqualTo(rejected.getCode()));
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
		assertThat(compared).isEqualTo(samples.size() - 1); // the DST-gap sample is rejected by both
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
