package com.myeongro.api.domain.saju.service;

import static com.myeongro.api.domain.reading.service.ReadingResponseSchema.orderedMap;
import static java.util.Map.entry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationStage;
import com.myeongro.api.domain.reading.service.DeclinedReadingFactory;
import com.myeongro.api.domain.reading.service.OpenAiResponseDiagnostics;
import com.myeongro.api.domain.reading.service.OpenAiReadingDiagnostics;
import com.myeongro.api.domain.reading.service.ReadingDeclineReason;
import com.myeongro.api.domain.reading.service.ReadingGenerationHandler;
import com.myeongro.api.domain.reading.service.ReadingGenerationMetadata;
import com.myeongro.api.domain.reading.service.ReadingResponseSchema;
import com.myeongro.api.domain.reading.service.ReadingRequestJson;
import com.myeongro.api.domain.saju.result.SajuReadingResult;

@Component
public class OpenAiSajuReadingGenerator implements ReadingGenerationHandler {

	private static final Logger log = LoggerFactory.getLogger(OpenAiSajuReadingGenerator.class);
	private static final int MAX_COMPLETION_TOKENS = 4_000;

	private final ChatModel chatModel;
	private final ObjectMapper objectMapper;
	private final String model;
	private final SajuPromptCatalog promptCatalog;
	private final SajuReadingResultValidator resultValidator;
	private final DeclinedReadingFactory declinedReadingFactory;

	public OpenAiSajuReadingGenerator(
		ChatModel chatModel,
		ObjectMapper objectMapper,
		@Value("${app.reading.openai.model}") String model,
		SajuPromptCatalog promptCatalog,
		SajuReadingResultValidator resultValidator,
		DeclinedReadingFactory declinedReadingFactory
	) {
		this.chatModel = chatModel;
		this.objectMapper = objectMapper;
		this.model = model;
		this.promptCatalog = promptCatalog;
		this.resultValidator = resultValidator;
		this.declinedReadingFactory = declinedReadingFactory;
	}

	@Override
	public ReadingKind kind() {
		return ReadingKind.SAJU;
	}

	@Override
	public ReadingGenerationMetadata metadata(String spreadType) {
		if (spreadType != null) {
			throw new IllegalArgumentException("Saju spread type must be empty");
		}
		return new ReadingGenerationMetadata("openai", model, promptCatalog.version());
	}

	@Override
	public GeneratedReading generate(
		ReadingKind kind,
		String spreadType,
		String question,
		Map<String, Object> input
	) {
		if (kind != ReadingKind.SAJU || spreadType != null) {
			throw new IllegalArgumentException("Saju input is required");
		}
		Prompt prompt;
		Map<String, Object> trustedCalculation;
		int targetYear;
		String focusArea;
		try {
			targetYear = requiredInteger(input.get("targetYear"));
			trustedCalculation = requiredMap(input.get("calculation"));
			focusArea = requiredText(input.get("focusArea"));
			prompt = new Prompt(
				List.of(
					new SystemMessage(promptCatalog.prompt()),
					new UserMessage(toPromptInput(
						trustedCalculation, focusArea, question
					))
				),
				options(targetYear, trustedCalculation)
			);
		} catch (RuntimeException | JsonProcessingException exception) {
			throw failure(OpenAiReadingGenerationStage.REQUEST_BUILD, exception);
		}

		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		} catch (RuntimeException exception) {
			throw failure(OpenAiReadingGenerationStage.PROVIDER_CALL, exception);
		}
		OpenAiResponseDiagnostics diagnostics = OpenAiResponseDiagnostics.from(response);

		JsonNode output;
		try {
			String content = response.getResult().getOutput().getText();
			output = objectMapper.readTree(content).required("output");
			OpenAiReadingDiagnostics.classification("saju", "none",
				(OpenAiChatOptions)prompt.getOptions(), output, objectMapper, response);
		} catch (RuntimeException | JsonProcessingException exception) {
			throw failure(
				OpenAiReadingGenerationStage.RESPONSE_PARSE,
				exception,
				diagnostics
			);
		}

		try {
			String resultType = output.required("resultType").textValue();
			if ("declined".equals(resultType)) {
				return declinedReadingFactory.create(ReadingDeclineReason.fromValue(
					output.required("reasonCode").textValue()
				));
			}
			if (!"reading".equals(resultType)) {
				throw new IllegalArgumentException("Unsupported saju response type");
			}
			SajuReadingResult result = objectMapper.treeToValue(
				output.required("reading"), SajuReadingResult.class
			);
			resultValidator.validate(result, targetYear, trustedCalculation);
			return new GeneratedReading(
				result.title(),
				objectMapper.convertValue(result, new TypeReference<>() {
				})
			);
		} catch (RuntimeException | JsonProcessingException exception) {
			throw failure(
				OpenAiReadingGenerationStage.RESPONSE_CONTRACT,
				exception,
				diagnostics
			);
		}
	}

	private OpenAiReadingGenerationException failure(
		OpenAiReadingGenerationStage stage,
		Exception exception
	) {
		return failure(stage, exception, OpenAiResponseDiagnostics.unavailable());
	}

	private OpenAiReadingGenerationException failure(
		OpenAiReadingGenerationStage stage,
		Exception exception,
		OpenAiResponseDiagnostics diagnostics
	) {
		log.warn(
			"Saju reading generation failed: stage={}, model={}, causeType={}, generationCount={}, "
				+ "finishReason={}, contentPresent={}, contentLength={}, completionTokens={}",
			stage,
			model,
			exception.getClass().getSimpleName(),
			diagnostics.generationCount(),
			diagnostics.finishReason(),
			diagnostics.contentPresent(),
			diagnostics.contentLength(),
			diagnostics.completionTokens()
		);
		return new OpenAiReadingGenerationException(stage, exception);
	}

	private String toPromptInput(
		Map<String, Object> trustedCalculation,
		String focusArea,
		String question
	) throws JsonProcessingException {
		Map<String, Object> untrustedUserInput = new LinkedHashMap<>();
		untrustedUserInput.put("focusArea", focusArea);
		untrustedUserInput.put("question", question);
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("trustedCalculation", trustedCalculation);
		message.put("untrustedUserInput", untrustedUserInput);
		return ReadingRequestJson.write(objectMapper, message);
	}

	private OpenAiChatOptions options(
		int targetYear,
		Map<String, Object> trustedCalculation
	) {
		return OpenAiChatOptions.builder()
			.model(model)
			.store(false)
			.maxCompletionTokens(MAX_COMPLETION_TOKENS)
			.temperature(1d)
			.responseFormat(ResponseFormat.builder()
				.type(ResponseFormat.Type.JSON_SCHEMA)
				.jsonSchema(ResponseFormat.JsonSchema.builder()
					.name("saju_birth_annual_question")
					.strict(true)
					.schema(ReadingResponseSchema.wrap(readingResultSchema(
						targetYear,
						resultValidator.availableEvidenceKeys(trustedCalculation)
					)))
					.build())
				.build())
			.build();
	}

	Map<String, Object> readingResultSchema(
		int targetYear,
		List<String> evidenceKeys
	) {
		Map<String, Object> evidence = orderedMap(
			entry("items", orderedMap(entry("enum", evidenceKeys), entry("type", "string"))),
			entry("minItems", 1),
			entry("type", "array")
		);
		Map<String, Object> section = orderedMap(
			entry("additionalProperties", false),
			entry("properties", orderedMap(
				entry("id", orderedMap(entry("enum", SajuReadingResultValidator.SECTION_IDS), entry("type", "string"))),
				entry("heading", textSchema()),
				entry("body", textSchema()),
				entry("evidenceKeys", evidence)
			)),
			entry("required", List.of("id", "heading", "body", "evidenceKeys")),
			entry("type", "object")
		);
		return orderedMap(
			entry("additionalProperties", false),
			entry("properties", orderedMap(
				entry("title", textSchema()),
				entry("natalSections", orderedMap(
					entry("items", section), entry("maxItems", 4), entry("minItems", 4), entry("type", "array")
				)),
				entry("annualReading", orderedMap(
					entry("additionalProperties", false),
					entry("properties", orderedMap(
						entry("year", orderedMap(entry("enum", List.of(targetYear)), entry("type", "integer"))),
						entry("heading", textSchema()), entry("body", textSchema()), entry("evidenceKeys", evidence)
					)),
					entry("required", List.of("year", "heading", "body", "evidenceKeys")),
					entry("type", "object")
				)),
				entry("questionReading", orderedMap(
					entry("additionalProperties", false),
					entry("properties", orderedMap(
						entry("heading", textSchema()), entry("body", textSchema()), entry("evidenceKeys", evidence)
					)),
					entry("required", List.of("heading", "body", "evidenceKeys")),
					entry("type", "object")
				)),
				entry("readingMode", orderedMap(
					entry("enum", java.util.Arrays.stream(com.myeongro.api.domain.reading.service.ReadingMode.values())
						.map(com.myeongro.api.domain.reading.service.ReadingMode::value).toList()),
					entry("type", "string")
				)),
				entry("summary", textSchema()),
				entry("disclaimer", textSchema()),
				entry("questionRedirected", orderedMap(entry("type", "boolean")))
			)),
			entry("required", List.of(
				"readingMode", "questionRedirected", "title", "summary", "natalSections", "annualReading",
				"questionReading", "disclaimer"
			)),
			entry("type", "object")
		);
	}

	private Map<String, Object> textSchema() {
		return orderedMap(entry("minLength", 1), entry("type", "string"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> requiredMap(Object value) {
		if (value instanceof Map<?, ?> map
			&& map.keySet().stream().allMatch(String.class::isInstance)) {
			return (Map<String, Object>)map;
		}
		throw new IllegalArgumentException("Saju map input is required");
	}

	private int requiredInteger(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		throw new IllegalArgumentException("Saju target year is required");
	}

	private String requiredText(Object value) {
		if (value instanceof String text && !text.isBlank()) {
			return text;
		}
		throw new IllegalArgumentException("Saju focus area is required");
	}
}
