package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.DirectIdentifierInputGuard;
import com.myeongro.api.domain.saju.calculation.*;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.saju.service.*;

class AiPreviewSajuInputPreparerTests {
	static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
	static AiPreviewSajuInputPreparer preparer() {
		var places = new SajuBirthPlaceCatalog(MAPPER, new ClassPathResource("saju/birth-places/kr-admin-v1.json"));
		var corrector = new TrueSolarTimeCorrector();
		var calculator = new SajuCalculationService(places, corrector,
			new ApproximateBirthTimeResolver(corrector, new LunarJavaFourPillarsAdapter()));
		return new AiPreviewSajuInputPreparer(MAPPER, new SajuReadingInputNormalizer(places),
			new SajuReadingInputAssembler(calculator, new SajuInterpretationInputMapper(),
				new SajuStoredCalculationMapper(), MAPPER), new DirectIdentifierInputGuard());
	}
	static AiPreviewCase birthCase(String precision, boolean annual) {
		var profile = new LinkedHashMap<String, Object>();
		profile.put("calendarType", "solar"); profile.put("birthDate", "1988-05-05");
		profile.put("birthTimePrecision", precision); profile.put("luckDirectionBasis", "unspecified");
		if (!"unknown".equals(precision)) { profile.put("birthTime", "12:00"); profile.put("provinceCode", "11"); }
		var input = new LinkedHashMap<String, Object>();
		input.put("birthProfile", profile); input.put("focusArea", "self"); input.put("targetYear", 2027);
		if (annual) input.put("reportType", "annual_monthly");
		return new AiPreviewCase("synthetic-birth", ReadingKind.SAJU, null, annual ? "" : "올해 성향을 읽어주세요.", input);
	}
	@ParameterizedTest @ValueSource(strings = {"exact", "approximate", "unknown"})
	void calculatesGeneralFromBirthWithoutPassingBirthFieldsToAi(String precision) throws Exception {
		var result = preparer().prepare(birthCase(precision, false));
		assertThat(result.previewCase().input()).containsKeys("calculation", "targetYear", "focusArea")
			.doesNotContainKeys("birthProfile", "calculationSnapshot");
		String json = MAPPER.writeValueAsString(result);
		assertThat(json).doesNotContain("1988-05-05", "12:00", "birthDate", "birthTimePrecision", "provinceCode");
		assertThat(result.calculationMetadata()).containsKeys("calculationVersion", "engine", "engineVersion");
	}
	@ParameterizedTest @ValueSource(strings = {"exact", "approximate", "unknown"})
	void annualUsesFreshProductionSnapshotAndDerivesDetailLevel(String precision) throws Exception {
		var result = preparer().prepare(birthCase(precision, true));
		var values = result.previewCase().input();
		@SuppressWarnings("unchecked") var snapshot = (Map<String, Object>)values.get("calculationSnapshot");
		var annual = com.myeongro.api.domain.saju.annual.SajuAnnualInput.from("", "self", 2027, snapshot, MAPPER);
		assertThat(annual.calendar().months()).hasSize(12);
		assertThat(annual.detailLevel()).isEqualTo("unknown".equals(precision) ? "brief" : "full");
		assertThat(MAPPER.writeValueAsString(result)).doesNotContain("1988-05-05", "12:00", "birthDate", "provinceCode");
	}
	@ParameterizedTest @ValueSource(strings = {"calculation", "calculationSnapshot", "requestId", "question", "unexpected"})
	void rejectsPrecomputedAndUnknownInputFields(String key) {
		var source = birthCase("exact", false);
		var input = new LinkedHashMap<>(source.input()); input.put(key, Map.of());
		assertThatThrownBy(() -> preparer().prepare(new AiPreviewCase(source.id(), source.kind(), null, source.question(), input)))
			.isInstanceOf(IllegalArgumentException.class);
	}
	@Test void requiresFixedYearAndRejectsNestedUnknownFields() {
		var source = birthCase("exact", false);
		var input = new LinkedHashMap<>(source.input()); input.remove("targetYear");
		assertThatThrownBy(() -> preparer().prepare(new AiPreviewCase(source.id(), source.kind(), null, source.question(), input)))
			.isInstanceOf(IllegalArgumentException.class);
		input.put("targetYear", 2027);
		@SuppressWarnings("unchecked") var profile = new LinkedHashMap<>((Map<String, Object>)input.get("birthProfile"));
		profile.put("cityCode", "11110"); input.put("birthProfile", profile);
		assertThatThrownBy(() -> preparer().prepare(new AiPreviewCase(source.id(), source.kind(), null, source.question(), input)))
			.isInstanceOf(IllegalArgumentException.class);
	}
	@Test void productionValidationRejectsUnknownTimeWithProvinceAndDirectIdentifiers() {
		var source = birthCase("unknown", false);
		var input = new LinkedHashMap<>(source.input());
		@SuppressWarnings("unchecked") var profile = new LinkedHashMap<>((Map<String, Object>)input.get("birthProfile"));
		profile.put("provinceCode", "11"); input.put("birthProfile", profile);
		assertThatThrownBy(() -> preparer().prepare(new AiPreviewCase(source.id(), source.kind(), null, source.question(), input)))
			.isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> preparer().prepare(new AiPreviewCase(source.id(), source.kind(), null,
			"연락처 person@example.com", source.input()))).isInstanceOf(RuntimeException.class);
	}
}
