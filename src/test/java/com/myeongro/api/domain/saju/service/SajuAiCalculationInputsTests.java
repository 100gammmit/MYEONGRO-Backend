package com.myeongro.api.domain.saju.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.reading.service.ReadingInputFingerprinter;
import com.myeongro.api.domain.saju.calculation.ApproximateBirthTimeResolver;
import com.myeongro.api.domain.saju.calculation.LunarJavaFourPillarsAdapter;
import com.myeongro.api.domain.saju.calculation.SajuCalculationService;
import com.myeongro.api.domain.saju.calculation.TrueSolarTimeCorrector;
import com.myeongro.api.domain.saju.controller.SajuReadingCreateRequest;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

/** B2: AI readings accept a city and calculation options, fingerprint them and store only the basis. */
class SajuAiCalculationInputsTests {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final SajuBirthPlaceCatalog CATALOG =
		new SajuBirthPlaceCatalog(MAPPER, new ClassPathResource("saju/birth-places/kr-admin-v1.json"));
	private final SajuReadingInputNormalizer normalizer = new SajuReadingInputNormalizer(CATALOG);

	@Test
	void normalizesCityAndOptionsIntoTheCalculationProfile() throws Exception {
		var input = normalizer.normalize(request(profile("exact", "14:30", "47", "47940"),
			"{\"dayBoundary\":\"HOUR_23\",\"equationOfTime\":false}"), 2026);

		assertThat(input.birthProfile())
			.containsEntry("provinceCode", "47").containsEntry("cityCode", "47940")
			.containsEntry("dayBoundary", "HOUR_23").containsEntry("equationOfTime", false);
	}

	@Test
	void defaultOptionsAndNoCityLeaveThePreB2ProfileUnchanged() {
		var omitted = normalizer.normalize(request(profile("exact", "14:30", "11", null), null), 2026);
		var explicit = normalizer.normalize(request(profile("exact", "14:30", "11", null),
			"{\"dayBoundary\":\"MIDNIGHT\",\"equationOfTime\":true}"), 2026);

		// Same keys as before B2, so fingerprints of default requests did not change at deploy.
		assertThat(omitted.birthProfile()).containsOnlyKeys(
			"calendarType", "birthDate", "birthTime", "birthTimePrecision", "provinceCode", "luckDirectionBasis");
		assertThat(explicit.birthProfile()).isEqualTo(omitted.birthProfile());
	}

	@Test
	void rejectsACityOutsideTheProvinceOrWithAnUnknownBirthTime() {
		assertThatThrownBy(() -> normalizer.normalize(request(profile("exact", "14:30", "11", "47940"), null), 2026))
			.isInstanceOfSatisfying(InvalidReadingRequestException.class, e -> {
				assertThat(e.getCode()).isEqualTo("INVALID_BIRTH_PLACE");
				assertThat(e.getField()).isEqualTo("birthProfile.cityCode");
			});
		assertThatThrownBy(() -> normalizer.normalize(request(profile("unknown", null, null, "11110"), null), 2026))
			.isInstanceOfSatisfying(InvalidReadingRequestException.class, e -> {
				assertThat(e.getCode()).isEqualTo("INVALID_BIRTH_PLACE");
				assertThat(e.getField()).isEqualTo("birthProfile.cityCode");
			});
	}

	@Test
	void rejectsMalformedCalculationOptionsWithFieldPaths() throws Exception {
		// options JSON -> {error code, field}; the code is what the frontend maps to a message.
		Map<String, String[]> cases = Map.of(
			"null", new String[] {"INVALID_CALCULATION_OPTIONS", "calculationOptions"},
			"\"MIDNIGHT\"", new String[] {"INVALID_CALCULATION_OPTIONS", "calculationOptions"},
			"{\"dayBoundary\":\"23\"}", new String[] {"INVALID_CALCULATION_OPTIONS", "calculationOptions.dayBoundary"},
			"{\"dayBoundary\":null}", new String[] {"INVALID_CALCULATION_OPTIONS", "calculationOptions.dayBoundary"},
			"{\"equationOfTime\":\"false\"}", new String[] {"INVALID_CALCULATION_OPTIONS", "calculationOptions.equationOfTime"},
			"{\"cityCode\":\"11110\"}", new String[] {"UNKNOWN_FIELD", "calculationOptions.cityCode"}
		);
		for (var entry : cases.entrySet()) {
			var request = request(profile("exact", "14:30", "11", null), entry.getKey());
			assertThatThrownBy(() -> normalizer.normalize(request, 2026)).as(entry.getKey())
				.isInstanceOfSatisfying(InvalidReadingRequestException.class, e -> {
					assertThat(e.getCode()).isEqualTo(entry.getValue()[0]);
					assertThat(e.getField()).isEqualTo(entry.getValue()[1]);
				});
		}
	}

	@Test
	void aRetryThatChangesOnlyTheCityOrAnOptionHasADifferentFingerprint() throws Exception {
		var fingerprinter = new ReadingInputFingerprinter(MAPPER,
			"tarot-idempotency-secret-for-tests-32-bytes!", "saju-idempotency-secret-for-tests-32-bytes!!",
			"tarot-selection-secret-for-tests-32-bytes!!!");
		String base = fingerprinter.fingerprint(normalizer.normalize(request(profile("exact", "14:30", "47", null), null), 2026));
		String same = fingerprinter.fingerprint(normalizer.normalize(
			request(profile("exact", "14:30", "47", null), "{\"dayBoundary\":\"MIDNIGHT\",\"equationOfTime\":true}"), 2026));
		String city = fingerprinter.fingerprint(normalizer.normalize(request(profile("exact", "14:30", "47", "47940"), null), 2026));
		String boundary = fingerprinter.fingerprint(normalizer.normalize(
			request(profile("exact", "14:30", "47", null), "{\"dayBoundary\":\"HOUR_23\"}"), 2026));
		String equation = fingerprinter.fingerprint(normalizer.normalize(
			request(profile("exact", "14:30", "47", null), "{\"equationOfTime\":false}"), 2026));

		assertThat(same).isEqualTo(base); // explicit defaults are the same request as omitted ones
		assertThat(java.util.Set.of(base, city, boundary, equation)).hasSize(4);
	}

	@Test
	void storesOnlyTheCalculationBasisNeverCodesLongitudeOrBirthData() {
		var corrector = new TrueSolarTimeCorrector();
		var calculation = new SajuCalculationService(CATALOG, corrector,
			new ApproximateBirthTimeResolver(corrector, new LunarJavaFourPillarsAdapter()));
		var assembler = new SajuReadingInputAssembler(calculation, new SajuInterpretationInputMapper(),
			new SajuStoredCalculationMapper(), MAPPER);

		record Case(SajuBirthProfileRequest profile, String options, Map<String, Object> basis) { }
		var cases = java.util.List.of(
			new Case(profile("exact", "23:30", "47", "47940"), "{\"dayBoundary\":\"HOUR_23\",\"equationOfTime\":false}",
				Map.of("dayBoundary", "HOUR_23", "equationOfTime", false, "coordinateSource", "city")),
			new Case(profile("approximate", "14:30", "11", null), null,
				Map.of("dayBoundary", "MIDNIGHT", "equationOfTime", true, "coordinateSource", "province")),
			new Case(profile("unknown", null, null, null), "{\"dayBoundary\":\"HOUR_23\"}",
				Map.of("dayBoundary", "HOUR_23", "equationOfTime", true, "coordinateSource", "reference")));
		for (String reportType : new String[] {null, "annual_monthly"}) {
			for (Case test : cases) {
				var input = normalizer.normalize(new SajuReadingCreateRequest(
					reportType == null ? "올해 흐름이 궁금해요" : null, UUID.randomUUID(), test.profile(), "career",
					2026, reportType, options(test.options())), 2026);
				var assembled = assembler.assemble(input, 2026, () -> { });
				@SuppressWarnings("unchecked")
				var stored = (Map<String, Object>)assembled.storedPayload().get("calculationSnapshot");
				assertThat(stored.get("calculationBasis")).as(test.toString()).isEqualTo(test.basis());
				String json = MAPPER.valueToTree(assembled.storedPayload()).toString();
				assertThat(json).doesNotContain("47940", "\"provinceCode\"", "\"cityCode\"", "\"birthDate\"",
					"\"birthTime\"", "\"longitude\"", "130.8");
				assertThat(MAPPER.valueToTree(assembled.aiPayload()).toString()).doesNotContain("calculationBasis");
			}
		}
	}

	private static SajuReadingCreateRequest request(SajuBirthProfileRequest profile, String options) {
		return new SajuReadingCreateRequest("올해 흐름이 궁금해요", UUID.randomUUID(), profile, "career", 2026, null,
			options(options));
	}

	private static JsonNode options(String json) {
		if (json == null) return null;
		try {
			return MAPPER.readTree(json);
		} catch (Exception exception) {
			throw new IllegalArgumentException(exception);
		}
	}

	private static SajuBirthProfileRequest profile(String precision, String time, String province, String city) {
		return new SajuBirthProfileRequest("solar", "1995-11-08", time, precision, province, city, "female");
	}
}
