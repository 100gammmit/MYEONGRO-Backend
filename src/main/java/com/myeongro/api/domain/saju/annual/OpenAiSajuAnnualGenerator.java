package com.myeongro.api.domain.saju.annual;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.service.DeclinedReadingFactory;
import com.myeongro.api.domain.reading.service.ReadingDeclineReason;
import com.myeongro.api.domain.reading.service.ReadingRequestJson;
import com.myeongro.api.domain.reading.service.ReadingResponseSchema;
import com.myeongro.api.domain.reading.service.ReadingMode;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationStage;

/** Delegated to by the sole SAJU handler; Preview imports it without the web workflow. */
public class OpenAiSajuAnnualGenerator {
	private final ChatModel chatModel;
	private final ObjectMapper mapper;
	private final String model;
	private final SajuAnnualPromptCatalog prompts;
	private final DeclinedReadingFactory declined;
	public OpenAiSajuAnnualGenerator(ChatModel chatModel, ObjectMapper mapper,
		@Value("${app.reading.openai.model}") String model, SajuAnnualPromptCatalog prompts, DeclinedReadingFactory declined) {
		this.chatModel = chatModel;
		this.mapper = mapper.copy().enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
			.disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT);
		this.mapper.setConfig(this.mapper.getDeserializationConfig()
			.without(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS));
		this.mapper.getFactory().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
		this.mapper.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
			.setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
			.setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
			.setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
		this.model = model; this.prompts = prompts; this.declined = declined;
	}
	public record Evaluation(GeneratedReading reading, Map<String, Object> metrics) { }
	public Evaluation generate(SajuAnnualInput input) {
		Prompt prompt;
		try {
			var user = new LinkedHashMap<String, Object>();
			user.put("trustedCalculation", input.trustedCalculation());
			user.put("calendarMonths", input.calendar().months());
			user.put("targetYear", input.calendar().targetYear());
			user.put("detailLevel", input.detailLevel());
			user.put("untrustedUserInput", Map.of("focusArea", input.focusArea(), "question", input.question()));
			var options = OpenAiChatOptions.builder().model(model).store(false).temperature(1d)
				.maxCompletionTokens(8_000)
				.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA)
					.jsonSchema(ResponseFormat.JsonSchema.builder().name("saju_annual_monthly").strict(true)
						.schema(ReadingResponseSchema.wrap(schema(input))).build()).build()).build();
			prompt = new Prompt(List.of(new SystemMessage(prompts.prompt()),
				new UserMessage(ReadingRequestJson.write(mapper, user))), options);
		} catch (Exception exception) { throw safeFailure(OpenAiReadingGenerationStage.REQUEST_BUILD, exception); }
		org.springframework.ai.chat.model.ChatResponse response;
		try { response = chatModel.call(prompt); }
		catch (RuntimeException exception) { throw safeFailure(OpenAiReadingGenerationStage.PROVIDER_CALL, exception); }
		Map<String, Object> metrics = new LinkedHashMap<>();
		if (response != null && response.getMetadata() != null) {
			var usage = response.getMetadata().getUsage();
			metrics.put("responseModel", response.getMetadata().getModel() == null ? model : response.getMetadata().getModel());
			if (usage != null) {
				metrics.put("promptTokens", usage.getPromptTokens());
				metrics.put("completionTokens", usage.getCompletionTokens());
				metrics.put("totalTokens", usage.getTotalTokens());
			}
		}
		if (response != null && response.getResult() != null) {
			metrics.put("finishReason", String.valueOf(response.getResult().getMetadata().getFinishReason()));
		}
		try {
			if (response == null || response.getResult() == null || !"stop".equalsIgnoreCase(response.getResult().getMetadata().getFinishReason())) {
				throw new IllegalArgumentException("Annual generation did not finish normally");
			}
			var root = mapper.readTree(response.getResult().getOutput().getText());
			if (!root.isObject() || root.size() != 1) throw new IllegalArgumentException("Invalid annual response envelope");
			var output = root.required("output");
			if (!output.isObject() || output.size() != 2) throw new IllegalArgumentException("Invalid annual output branch");
			if (!output.required("resultType").isTextual()) throw new IllegalArgumentException("Text result type required");
			GeneratedReading reading;
			if ("declined".equals(output.path("resultType").asText())) {
				String reason = output.required("reasonCode").textValue();
				if (reason == null || !ReadingDeclineReason.modelSelectableValues().contains(reason)) {
					throw new IllegalArgumentException("Unsupported model decline reason");
				}
				reading = declined.create(ReadingDeclineReason.fromValue(reason));
			} else if ("reading".equals(output.path("resultType").asText())) {
				var result = readResult(output.required("reading"), input);
				new SajuAnnualResultValidator().validate(result, input);
				reading = new GeneratedReading(result.title(), mapper.convertValue(result, new TypeReference<>() { }));
			} else { throw new IllegalArgumentException("Unsupported annual result type"); }
			// Native token details are not copied: providers may add unrelated data.
			return new Evaluation(reading, Map.copyOf(metrics));
		} catch (Exception exception) { throw new SajuAnnualGenerationException(exception, metrics); }
	}

	public Map<String, Object> schema(SajuAnnualInput input) {
		var evidence = array(Map.of("type", "string", "enum", input.trustedCalculation().keySet().stream().sorted().toList()), 1, 12);
		var fields = new LinkedHashMap<String, Object>();
		fields.put("heading", text()); fields.put("body", text()); fields.put("evidenceKeys", evidence);
		var section = object(fields);
		var monthFields = new LinkedHashMap<String, Object>();
		for (var month : input.calendar().months()) {
			var keys = month.periodIds().stream().map(id -> "monthlyFlow." + id).toList();
			monthFields.put("m%02d".formatted(month.month()), sectionSchema(input, keys));
		}
		var periodFields = new LinkedHashMap<String, Object>();
		for (var period : input.calendar().periods()) {
			periodFields.put(period.id(), sectionSchema(input, List.of("monthlyFlow." + period.id())));
		}
		var reading = new LinkedHashMap<String, Object>();
		reading.put("reportType", Map.of("type", "string", "enum", List.of("annual_monthly")));
		reading.put("detailLevel", Map.of("type", "string", "enum", List.of(input.detailLevel())));
		reading.put("targetYear", Map.of("type", "integer", "enum", List.of(input.calendar().targetYear())));
		reading.put("readingMode", Map.of("type", "string", "enum", java.util.Arrays.stream(ReadingMode.values()).map(ReadingMode::value).toList()));
		reading.put("questionRedirected", Map.of("type", "boolean"));
		reading.put("title", text()); reading.put("annualOverview", section);
		reading.put("periods", "brief".equals(input.detailLevel()) ? array(section, 0, 0) : object(periodFields));
		reading.put("months", object(monthFields));
		reading.put("questionReading", input.question().isBlank() ? Map.of("type", "null") : section);
		reading.put("summary", text()); reading.put("disclaimer", text());
		return object(reading);
	}
	private Map<String, Object> sectionSchema(SajuAnnualInput input, List<String> monthlyKeys) {
		var keys = input.trustedCalculation().keySet().stream()
			.filter(key -> !key.startsWith("monthlyFlow.") || monthlyKeys.contains(key)).sorted().toList();
		var fields = new LinkedHashMap<String, Object>();
		fields.put("heading", text()); fields.put("body", text());
		fields.put("evidenceKeys", array(Map.of("type", "string", "enum", keys), monthlyKeys.size(), 12));
		return object(fields);
	}

	/** Provider keys are schema-fixed IDs. The service, not the model, assigns ordered DTO identifiers. */
	private SajuAnnualResult readResult(com.fasterxml.jackson.databind.JsonNode node, SajuAnnualInput input) throws Exception {
		if (!(node instanceof com.fasterxml.jackson.databind.node.ObjectNode reading)) throw new IllegalArgumentException("Reading object required");
		var normalized = reading.deepCopy();
		var months = node.required("months");
		if (!months.isObject() || months.size() != 12) throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.INCOMPLETE);
		var monthArray = mapper.createArrayNode();
		for (int month = 1; month <= 12; month++) {
			var section = mapper.treeToValue(months.required("m%02d".formatted(month)), SajuAnnualResult.Section.class);
			monthArray.add(mapper.valueToTree(new SajuAnnualResult.Month(month, section.heading(), section.body(), section.evidenceKeys())));
		}
		normalized.set("months", monthArray);
		var periods = node.required("periods");
		var periodArray = mapper.createArrayNode();
		if ("brief".equals(input.detailLevel())) {
			if (!periods.isArray() || !periods.isEmpty()) throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.INCOMPLETE);
		} else {
			if (!periods.isObject() || periods.size() != input.calendar().periods().size()) throw new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.INCOMPLETE);
			for (var period : input.calendar().periods()) {
				var section = mapper.treeToValue(periods.required(period.id()), SajuAnnualResult.Section.class);
				periodArray.add(mapper.valueToTree(new SajuAnnualResult.Period(period.id(), section.heading(), section.body(), section.evidenceKeys())));
			}
		}
		normalized.set("periods", periodArray);
		return mapper.treeToValue(normalized, SajuAnnualResult.class);
	}
	private OpenAiReadingGenerationException safeFailure(OpenAiReadingGenerationStage stage, Exception exception) {
		return new OpenAiReadingGenerationException(stage,
			new IllegalStateException("Annual generation failed: " + exception.getClass().getSimpleName()));
	}
	private Map<String, Object> text() { return Map.of("type", "string", "minLength", 1); }
	private Map<String, Object> array(Map<String, Object> item, int min, int max) {
		return Map.of("type", "array", "items", item, "minItems", min, "maxItems", max);
	}
	private Map<String, Object> object(Map<String, Object> fields) {
		var properties = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
		return ReadingResponseSchema.orderedMap(Map.entry("type", "object"), Map.entry("additionalProperties", false),
			Map.entry("properties", properties), Map.entry("required", List.copyOf(fields.keySet())));
	}
}
