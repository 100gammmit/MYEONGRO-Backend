package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

class SajuCalculationGoldenTests {

	@Test
	void matchesFrozenSajuKoV1Fixtures() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		SajuBirthPlaceCatalog catalog = new SajuBirthPlaceCatalog(
			objectMapper,
			new ClassPathResource("saju/birth-places/kr-admin-v1.json")
		);
		TrueSolarTimeCorrector corrector = new TrueSolarTimeCorrector();
		LunarJavaFourPillarsAdapter adapter = new LunarJavaFourPillarsAdapter();
		SajuCalculationService service = new SajuCalculationService(
			catalog,
			corrector,
			adapter,
			new ApproximateBirthTimeResolver(corrector, adapter)
		);
		JsonNode fixture = objectMapper.readTree(
			new ClassPathResource("saju/golden/saju-ko-v1.json").getInputStream()
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
			}
			actual.add(testCase.path("id").asText() + "=" + summary(snapshot));
			expected.add(testCase.path("id").asText() + "="
				+ testCase.path("expectedSummary").asText());
		}
		assertThat(actual).containsExactlyElementsOf(expected);
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
