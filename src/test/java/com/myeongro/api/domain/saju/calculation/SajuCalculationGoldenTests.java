package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

class SajuCalculationGoldenTests {

	@Test
	void matchesFrozenSajuKoV3Fixtures() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		SajuCalculationService service = service(objectMapper);
		JsonNode fixture = objectMapper.readTree(
			new ClassPathResource("saju/golden/saju-ko-v3.json").getInputStream()
		);
		assertThat(fixture.path("calculationVersion").asText())
			.isEqualTo(SajuCalculationRules.CALCULATION_VERSION);
		assertThat(fixture.path("engineVersion").asText())
			.isEqualTo(SajuCalculationRules.ENGINE_VERSION);

		List<String> actual = new ArrayList<>();
		List<String> expected = new ArrayList<>();
		for (JsonNode testCase : fixture.path("cases")) {
			Map<String, Object> profile = objectMapper.convertValue(
				testCase.path("profile"), new TypeReference<>() {
				}
			);
			SajuCalculationSnapshot snapshot = service.calculate(
				profile, testCase.path("targetYear").asInt()
			);
			if ("unknown-birth-time".equals(testCase.path("id").asText())) {
				assertThat(snapshot.uncertainty().candidateCount()).isEqualTo(1440);
				assertThat(snapshot.limitations()).contains(
					"BIRTH_TIME_UNKNOWN", "MONTH_PILLAR_UNCERTAIN",
					"DAY_PILLAR_UNCERTAIN", "TIME_PILLAR_UNCERTAIN"
				);
				assertThat(snapshot.annualFortune().ganZhi()).isEqualTo("丙午");
				assertThat(snapshot.annualFortune().stemTenGod()).isNull();
				assertThat(snapshot.uncertainty().varyingFields())
					.contains("annualFortune.stemTenGod");
			}
			if ("lichun-before-female".equals(testCase.path("id").asText())) {
				assertThat(snapshot.timeCorrection().engineCivilTime())
					.isEqualTo("2020-02-04T17:00");
			}
			if ("lichun-after-female".equals(testCase.path("id").asText())) {
				assertThat(snapshot.timeCorrection().engineCivilTime())
					.isEqualTo("2020-02-04T17:10");
			}
			actual.add(testCase.path("id").asText() + "=" + summary(snapshot));
			expected.add(testCase.path("id").asText() + "="
				+ testCase.path("expectedSummary").asText());
		}
		assertThat(actual).containsExactlyElementsOf(expected);
	}

	@Test
	void rejectsExactDstGapAndMergesBothOverlapInstants() {
		SajuCalculationService service = service(new ObjectMapper());
		Map<String, Object> gap = profile("1988-05-08", "02:30");
		Map<String, Object> overlap = profile("1988-10-09", "02:30");

		assertThatThrownBy(() -> service.calculate(gap, 2026))
			.isInstanceOf(InvalidReadingRequestException.class)
			.extracting("code", "field")
			.containsExactly("INVALID_BIRTH_TIME", "birthProfile.birthTime");

		SajuCalculationSnapshot snapshot = service.calculate(overlap, 2026);
		assertThat(snapshot.timeCorrection()).isNull();
		assertThat(snapshot.uncertainty().candidateCount()).isEqualTo(2);
		assertThat(snapshot.uncertainty().candidateZoneOffsets())
			.containsExactly("+10:00", "+09:00");
		assertThat(snapshot.limitations()).contains("DST_OVERLAP_AMBIGUOUS");
	}

	@Test
	void skipsGapMinutesAndExpandsBothOverlapInstantsForApproximateTime() {
		SajuCalculationService service = service(new ObjectMapper());
		Map<String, Object> gap = profile("1988-05-08", "02:30", "approximate");
		Map<String, Object> overlap = profile("1988-10-09", "02:30", "approximate");

		SajuCalculationSnapshot gapSnapshot = service.calculate(gap, 2026);
		SajuCalculationSnapshot overlapSnapshot = service.calculate(overlap, 2026);

		assertThat(gapSnapshot.uncertainty().candidateCount()).isEqualTo(61);
		assertThat(gapSnapshot.limitations()).contains("DST_GAP_SKIPPED");
		assertThat(overlapSnapshot.uncertainty().candidateCount()).isEqualTo(181);
		assertThat(overlapSnapshot.limitations()).contains("DST_OVERLAP_AMBIGUOUS");
	}

	private SajuCalculationService service(ObjectMapper objectMapper) {
		SajuBirthPlaceCatalog catalog = new SajuBirthPlaceCatalog(
			objectMapper,
			new ClassPathResource("saju/birth-places/kr-admin-v1.json")
		);
		TrueSolarTimeCorrector corrector = new TrueSolarTimeCorrector();
		LunarJavaFourPillarsAdapter adapter = new LunarJavaFourPillarsAdapter();
		return new SajuCalculationService(
			catalog, corrector, new ApproximateBirthTimeResolver(corrector, adapter)
		);
	}

	private Map<String, Object> profile(String date, String time) {
		return profile(date, time, "exact");
	}

	private Map<String, Object> profile(String date, String time, String precision) {
		return Map.of(
			"birthDate", date, "birthTime", time, "birthTimePrecision", precision,
			"provinceCode", "11", "luckDirectionBasis", "male"
		);
	}

	private String summary(SajuCalculationSnapshot snapshot) {
		var pillars = snapshot.pillars();
		return String.join("/",
			value(pillars.year()), value(pillars.month()), value(pillars.day()),
			value(pillars.time()), text(snapshot.dayMaster()),
			snapshot.annualFortune() == null ? "-" : snapshot.annualFortune().ganZhi(),
			snapshot.luckCycle() == null ? "-" : snapshot.luckCycle().direction()
		);
	}

	private String value(SajuCalculationSnapshot.Pillar pillar) {
		return pillar == null ? "-" : pillar.ganZhi();
	}

	private String text(String value) {
		return value == null ? "-" : value;
	}
}
