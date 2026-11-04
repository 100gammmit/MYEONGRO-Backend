package com.myeongro.api.domain.saju.annual;

import static org.assertj.core.api.Assertions.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.openai.OpenAiChatOptions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.saju.calculation.*;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.domain.saju.service.SajuStoredCalculationMapper;
import com.myeongro.api.domain.reading.service.*;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;

class SajuAnnualTests {
	static final ObjectMapper MAPPER = new ObjectMapper();
	static Map<String, Object> snapshot(String precision, String time, String date) {
		var corrector = new TrueSolarTimeCorrector();
		var service = new SajuCalculationService(new SajuBirthPlaceCatalog(MAPPER,
			new ClassPathResource("saju/birth-places/kr-admin-v1.json")), corrector,
			new ApproximateBirthTimeResolver(corrector, new LunarJavaFourPillarsAdapter()));
		var profile = new LinkedHashMap<String, Object>(Map.of("birthDate", date,
			"birthTimePrecision", precision, "luckDirectionBasis", "unspecified"));
		if (!precision.equals("unknown")) { profile.put("birthTime", time); profile.put("provinceCode", "11"); }
		var calculated = service.calculate(profile, 2027);
		var stored = new LinkedHashMap<>(new SajuStoredCalculationMapper().map(calculated));
		stored.put("monthlyCalendar", MAPPER.convertValue(new SajuMonthlyCalendarService().calculate(2027, calculated.dayMaster()), Map.class));
		return stored;
	}
	static SajuAnnualInput input(String question) {
		return SajuAnnualInput.from(question, "career", 2027, snapshot("exact", "12:00", "1992-08-17"), MAPPER);
	}
	static SajuAnnualResult valid(SajuAnnualInput input) {
		var overview = new SajuAnnualResult.Section("올해 살펴볼 일", "올해의 설명입니다.", List.of("annualFlow"));
		var months = input.calendar().months().stream().map(m -> new SajuAnnualResult.Month(m.month(),
			m.month() + "월의 설명", "월별 설명입니다.", List.of("monthlyFlow." + m.periodIds().getFirst()))).toList();
		var periods = input.calendar().periods().stream().map(p -> new SajuAnnualResult.Period(p.id(),
			"관계를 살피는 때", "구간 설명입니다.", List.of("monthlyFlow." + p.id()))).toList();
		return new SajuAnnualResult("annual_monthly", 2027, ReadingMode.STANDARD, false,
			"올해 맡은 일을 살펴봐요", overview, months, periods, input.question().isBlank() ? null : overview,
			"맡은 일을 살피는 해입니다.", "오락과 자기 성찰을 위한 참고이며 전문 조언을 대신하지 않습니다.");
	}
	@Test void mergesOnlyContinuousIdenticalPeriodsAndPreservesEveryCalendarSlice() throws Exception {
		var input = input("");
		assertThat(input.calendar().periods()).hasSize(13);
		for (int index = 0; index < 11; index++) assertThat(input.calendar().months().get(index).periodIds().getLast())
			.isEqualTo(input.calendar().months().get(index + 1).periodIds().getFirst());
		assertThat(MAPPER.readValue(MAPPER.writeValueAsString(input.calendar()), SajuAnnualCalendar.class)).isEqualTo(input.calendar());
		assertThat(input.calendar().periods().getFirst().startInclusive()).startsWith("2027-01-01T00:00:00");
		assertThat(input.calendar().periods().getLast().endExclusive()).startsWith("2028-01-01T00:00:00");
		assertThat(((Map<?, ?>)input.trustedCalculation().get("monthlyFlow.p02")).get("yearChangedFromPreviousPeriod")).isEqualTo(false);
		assertThat(((Map<?, ?>)input.trustedCalculation().get("monthlyFlow.p03")).get("yearChangedFromPreviousPeriod")).isEqualTo(true);
	}
	@Test void rejectsWrongYearGapsAndPersonalTenGodWithoutDayMaster() {
		var snapshot = snapshot("exact", "12:00", "1992-08-17");
		assertThatThrownBy(() -> SajuAnnualInput.from("", "self", 2026, snapshot, MAPPER)).isInstanceOf(IllegalArgumentException.class);
		snapshot.remove("dayMaster");
		assertThatThrownBy(() -> SajuAnnualInput.from("", "self", 2027, snapshot, MAPPER)).isInstanceOf(IllegalArgumentException.class);
		var calendar = new SajuMonthlyCalendarService().calculate(2027, null);
		var brokenMonths = new ArrayList<>(calendar.months());
		var january = brokenMonths.getFirst();
		var slices = new ArrayList<>(january.periods());
		var first = slices.getFirst();
		slices.set(0, new SajuMonthlyCalendar.Period("2027-01-01T00:01:00+09:00", first.endExclusive(),
			first.yearGanZhi(), first.monthGanZhi(), first.stemTenGod()));
		brokenMonths.set(0, new SajuMonthlyCalendar.CalendarMonth(1, slices));
		assertThatThrownBy(() -> SajuAnnualCalendar.from(new SajuMonthlyCalendar(calendar.calculationVersion(),
			calendar.timeZone(), 2027, brokenMonths))).isInstanceOf(IllegalArgumentException.class);
		var months = new ArrayList<>(calendar.months()); months.removeLast();
		assertThatThrownBy(() -> SajuAnnualCalendar.from(new SajuMonthlyCalendar(calendar.calculationVersion(),
			calendar.timeZone(), 2027, months))).isInstanceOf(IllegalArgumentException.class);
	}
	@Test void acceptsUnknownDayMasterWithoutInventingPersonalEvidence() {
		var snapshot = snapshot("unknown", "", "1988-05-05");
		assertThat(snapshot).doesNotContainKey("dayMaster");
		var input = SajuAnnualInput.from(null, "self", 2027, snapshot, MAPPER);
		assertThat(input.question()).isEmpty();
		assertThat(input.trustedCalculation()).doesNotContainKeys("dayMaster", "tenGods");
		assertThat(input.calendar().periods()).allMatch(p -> p.stemTenGod() == null);
		new SajuAnnualResultValidator().validate(valid(input), input);
	}
	@Test void validatesCompletenessOrderingOwnEvidenceAndNoFabricatedQuestion() {
		var input = input(""); var result = valid(input); var validator = new SajuAnnualResultValidator();
		validator.validate(result, input);
		var json = MAPPER.valueToTree(result);
		((com.fasterxml.jackson.databind.node.ArrayNode)json.get("months")).remove(11);
		var incomplete = json;
		assertThatThrownBy(() -> validator.validate(MAPPER.treeToValue(incomplete, SajuAnnualResult.class), input)).isInstanceOf(IllegalArgumentException.class);
		json = MAPPER.valueToTree(result);
		((com.fasterxml.jackson.databind.node.ObjectNode)json.get("periods").get(1)).put("periodId", "p01");
		var duplicate = json;
		assertThatThrownBy(() -> validator.validate(MAPPER.treeToValue(duplicate, SajuAnnualResult.class), input)).isInstanceOf(IllegalArgumentException.class);
		json = MAPPER.valueToTree(result);
		((com.fasterxml.jackson.databind.node.ObjectNode)json.get("periods").get(0)).set("evidenceKeys", MAPPER.valueToTree(List.of("monthlyFlow.p02")));
		var wrongEvidence = json;
		assertThatThrownBy(() -> validator.validate(MAPPER.treeToValue(wrongEvidence, SajuAnnualResult.class), input)).isInstanceOf(IllegalArgumentException.class);
		json = MAPPER.valueToTree(result);
		((com.fasterxml.jackson.databind.node.ObjectNode)json).set("questionReading", MAPPER.valueToTree(result.annualOverview()));
		var fabricated = json;
		assertThatThrownBy(() -> validator.validate(MAPPER.treeToValue(fabricated, SajuAnnualResult.class), input)).isInstanceOf(IllegalArgumentException.class);
	}
	@Test void rejectsIdentifiersAndLongQuestionsBeforeAnyProviderCall() {
		var snapshot = snapshot("exact", "12:00", "1992-08-17");
		assertThatThrownBy(() -> SajuAnnualInput.from("연락처 person@example.com", "career", 2027, snapshot, MAPPER)).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> SajuAnnualInput.from("가".repeat(301), "career", 2027, snapshot, MAPPER)).isInstanceOf(IllegalArgumentException.class);
	}
	@Test void dropsExtraSnapshotFieldsAndDoesNotRegisterWithProductionRouter() throws Exception {
		var snapshot = snapshot("exact", "12:00", "1992-08-17");
		snapshot.put("birthDate", "synthetic-private-date");
		snapshot.put("coordinates", Map.of("longitude", 127));
		var prepared = SajuAnnualInput.from("", "self", 2027, snapshot, MAPPER);
		assertThat(MAPPER.writeValueAsString(prepared.trustedCalculation())).doesNotContain("synthetic-private-date", "coordinates", "longitude");
		assertThat(ReadingGenerationHandler.class.isAssignableFrom(OpenAiSajuAnnualGenerator.class)).isFalse();
		assertThat(OpenAiSajuAnnualGenerator.class.isAnnotationPresent(org.springframework.stereotype.Component.class)).isFalse();
	}
	static SajuAnnualPromptCatalog prompts() {
		return new SajuAnnualPromptCatalog(new ReadingSafetyPrompt(new ClassPathResource("prompts/reading/safety/reading-safety-ko-v3.md")),
			new ClassPathResource("prompts/saju/common-ko-v11.md"),
			new ClassPathResource("prompts/saju/interpretation/interpretation-guide-ko-v7.md"),
			new ClassPathResource("prompts/saju/reports/annual-monthly/annual-monthly-ko-v3.md"));
	}
	@Test void generatorUsesStrictSchemaNoRawBirthDataAndRejectsTruncation() throws Exception {
		var input = input(""); var result = valid(input);
		String content = MAPPER.writeValueAsString(Map.of("output", Map.of("resultType", "reading", "reading", result)));
		ChatModel model = prompt -> {
			var options = (OpenAiChatOptions)prompt.getOptions();
			assertThat(options.getStore()).isFalse();
			assertThat(options.getResponseFormat().getJsonSchema().getStrict()).isTrue();
			assertThat(prompt.getUserMessage().getText()).doesNotContain("birthDate", "birthTime", "provinceCode", "longitude", "startInclusive", "endExclusive");
			return new ChatResponse(List.of(new Generation(new AssistantMessage(content), ChatGenerationMetadata.builder().finishReason("STOP").build())));
		};
		var generator = new OpenAiSajuAnnualGenerator(model, MAPPER, "test-model", prompts(), new DeclinedReadingFactory());
		assertThat(generator.generate(input).reading().payload().get("reportType")).isEqualTo("annual_monthly");
		assertThat(MAPPER.writeValueAsString(generator.schema(input))).doesNotContain("uniqueItems", "natalSections");
		var properties = (Map<?, ?>)generator.schema(input).get("properties");
		assertThat(new ArrayList<>(properties.keySet()).indexOf("periods"))
			.isLessThan(new ArrayList<>(properties.keySet()).indexOf("months"));
		var truncated = new OpenAiSajuAnnualGenerator(prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(content),
			ChatGenerationMetadata.builder().finishReason("length").build()))), MAPPER, "test", prompts(), new DeclinedReadingFactory());
		assertThatThrownBy(() -> truncated.generate(input)).isInstanceOf(OpenAiReadingGenerationException.class);
	}

	@Test void refusesUnknownResponseFieldsEvenWhenApplicationMapperIgnoresThem() throws Exception {
		var input = input("");
		var result = MAPPER.valueToTree(valid(input));
		((com.fasterxml.jackson.databind.node.ObjectNode)result).put("inventedDate", "2027-05-01");
		String content = MAPPER.writeValueAsString(Map.of("output", Map.of("resultType", "reading", "reading", result)));
		var lenient = MAPPER.copy().disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		ChatModel model = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(content),
			ChatGenerationMetadata.builder().finishReason("STOP").build())));
		var generator = new OpenAiSajuAnnualGenerator(model, lenient, "test", prompts(), new DeclinedReadingFactory());
		assertThatThrownBy(() -> generator.generate(input)).isInstanceOf(SajuAnnualGenerationException.class);
	}

	@Test void preservesDeclineBranchAndRejectsInvalidSafetyCombination() {
		var input = input("합성된 안전 평가 질문");
		ChatModel model = prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(
			"{\"output\":{\"resultType\":\"declined\",\"reasonCode\":\"CRISIS_OR_IMMEDIATE_DANGER\"}}"),
			ChatGenerationMetadata.builder().finishReason("STOP").build())));
		var generator = new OpenAiSajuAnnualGenerator(model, MAPPER, "test", prompts(), new DeclinedReadingFactory());
		assertThat(generator.generate(input).reading().payload().get("resultType")).isEqualTo("declined");
		var result = valid(input);
		var invalid = new SajuAnnualResult(result.reportType(), result.targetYear(), ReadingMode.STANDARD, true,
			result.title(), result.annualOverview(), result.months(), result.periods(), result.questionReading(), result.summary(), result.disclaimer());
		assertThatThrownBy(() -> new SajuAnnualResultValidator().validate(invalid, input)).isInstanceOf(IllegalArgumentException.class);
	}
}
