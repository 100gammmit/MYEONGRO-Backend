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

	/** v4 stays frozen through the LEGACY settings it was recorded with; production no longer uses them. */
	@Test
	void matchesFrozenSajuKoV4Fixtures() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		SajuCalculationService service = service(objectMapper);
		SajuBirthPlaceCatalog catalog = catalog(objectMapper);
		assertFixture(objectMapper, "saju/golden/saju-ko-v4.json", SajuCalculationRules.LEGACY_DAY_OF_YEAR_VERSION,
			(profile, year) -> legacy(service, catalog, profile, year));
	}

	@Test
	void matchesFrozenSajuKoV5FixturesThroughTheAiReadingPath() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		SajuCalculationService service = service(objectMapper);
		assertFixture(objectMapper, "saju/golden/saju-ko-v5.json", SajuCalculationRules.CALCULATION_VERSION,
			service::calculate);
	}

	/** The v5 boundary cases are exactly where the precise equation of time moves the chart away from v4. */
	@Test
	void v5BoundaryCasesDifferFromV4OnlyAsRecorded() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		SajuCalculationService service = service(objectMapper);
		SajuBirthPlaceCatalog catalog = catalog(objectMapper);
		JsonNode fixture = objectMapper.readTree(new ClassPathResource("saju/golden/saju-ko-v5.json").getInputStream());
		int divergent = 0;
		for (JsonNode testCase : fixture.path("cases")) {
			Map<String, Object> profile = objectMapper.convertValue(testCase.path("profile"), new TypeReference<>() { });
			String v4 = summary(legacy(service, catalog, profile, testCase.path("targetYear").asInt()));
			String expectedV4 = testCase.has("v4Summary")
				? testCase.path("v4Summary").asText() : testCase.path("expectedSummary").asText();
			assertThat(v4).as(testCase.path("id").asText()).isEqualTo(expectedV4);
			if (testCase.has("v4Summary")) {
				assertThat(expectedV4).isNotEqualTo(testCase.path("expectedSummary").asText());
				divergent++;
			}
		}
		assertThat(divergent).isEqualTo(4);
	}

	private void assertFixture(ObjectMapper objectMapper, String path, String version,
		java.util.function.BiFunction<Map<String, Object>, Integer, SajuCalculationSnapshot> calculate) throws Exception {
		JsonNode fixture = objectMapper.readTree(
			new ClassPathResource(path).getInputStream()
		);
		assertThat(fixture.path("calculationVersion").asText()).isEqualTo(version);
		assertThat(fixture.path("engineVersion").asText())
			.isEqualTo(SajuCalculationRules.ENGINE_VERSION);

		List<String> actual = new ArrayList<>();
		List<String> expected = new ArrayList<>();
		for (JsonNode testCase : fixture.path("cases")) {
			Map<String, Object> profile = objectMapper.convertValue(
				testCase.path("profile"), new TypeReference<>() {
				}
			);
			SajuCalculationSnapshot snapshot = calculate.apply(
				profile, testCase.path("targetYear").asInt()
			);
			assertThat(snapshot.calculationVersion()).isEqualTo(version);
			if ("unknown-birth-time".equals(testCase.path("id").asText())) {
				assertThat(snapshot.uncertainty().candidateCount()).isEqualTo(1440);
				assertThat(snapshot.limitations()).contains(
					"BIRTH_TIME_UNKNOWN", "MONTH_PILLAR_UNCERTAIN",
					"DAY_PILLAR_UNCERTAIN", "TIME_PILLAR_UNCERTAIN"
				);
				assertThat(snapshot.annualFortune().ganZhi()).isEqualTo("병오");
				assertThat(snapshot.annualFortune().stemTenGod()).isNull();
				assertThat(snapshot.uncertainty().varyingFields())
					.contains("annualFortune.stemTenGod");
				// Only the fixed year pillar 무진 is counted: 무 earth, 진 earth.
				assertThat(snapshot.fiveElements()).containsExactlyInAnyOrderEntriesOf(Map.of(
					"wood", 0, "fire", 0, "earth", 2, "metal", 0, "water", 0
				));
			}
			assertThat(objectMapper.writeValueAsString(snapshot))
				.as("stored terms are Korean for %s", testCase.path("id").asText())
				.doesNotContainPattern("\\p{IsHan}");
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

	private SajuCalculationSnapshot legacy(SajuCalculationService service, SajuBirthPlaceCatalog catalog,
		Map<String, Object> profile, int targetYear) {
		double longitude = "unknown".equals(profile.get("birthTimePrecision"))
			? SajuCalculationRules.KOREA_REFERENCE_LONGITUDE
			: catalog.requireProvince((String)profile.get("provinceCode")).longitude();
		return service.calculateForChart(profile, targetYear, longitude, SajuCalculationSettings.LEGACY,
			SajuCandidateObserver.NONE, () -> { });
	}

	private SajuBirthPlaceCatalog catalog(ObjectMapper objectMapper) {
		return new SajuBirthPlaceCatalog(objectMapper, new ClassPathResource("saju/birth-places/kr-admin-v1.json"));
	}

	private SajuCalculationService service(ObjectMapper objectMapper) {
		SajuBirthPlaceCatalog catalog = catalog(objectMapper);
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
