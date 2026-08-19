package com.myeongro.api.domain.saju.service;

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
import com.myeongro.api.domain.reading.service.ReadingDeclineReason;
import com.myeongro.api.domain.reading.service.ReadingGenerationHandler;
import com.myeongro.api.domain.reading.service.ReadingGenerationMetadata;
import com.myeongro.api.domain.reading.service.ReadingResponseSchema;
import com.myeongro.api.domain.saju.result.SajuReadingResult;

@Component
public class OpenAiSajuReadingGenerator implements ReadingGenerationHandler {

	private static final Logger log = LoggerFactory.getLogger(OpenAiSajuReadingGenerator.class);
	private static final int MAX_COMPLETION_TOKENS = 4_000;

	private final ChatModel chatModel;
	private final ObjectMapper objectMapper;
	private final String model;
	private final SajuPromptCatalog promptCatalog;
	private final SajuInterpretationInputMapper interpretationInputMapper;
	private final SajuReadingResultValidator resultValidator;
	private final DeclinedReadingFactory declinedReadingFactory;

	public OpenAiSajuReadingGenerator(
		ChatModel chatModel,
		ObjectMapper objectMapper,
		@Value("${app.reading.openai.model}") String model,
		SajuPromptCatalog promptCatalog,
		SajuInterpretationInputMapper interpretationInputMapper,
		SajuReadingResultValidator resultValidator,
		DeclinedReadingFactory declinedReadingFactory
	) {
		this.chatModel = chatModel;
		this.objectMapper = objectMapper;
		this.model = model;
		this.promptCatalog = promptCatalog;
		this.interpretationInputMapper = interpretationInputMapper;
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
			Map<String, Object> snapshot = requiredMap(input.get("calculationSnapshot"));
			targetYear = requiredInteger(input.get("targetYear"));
			trustedCalculation = interpretationInputMapper.map(snapshot, targetYear);
			focusArea = requiredText(input.get("focusArea"));
			prompt = new Prompt(
				List.of(
					new SystemMessage(promptCatalog.prompt()),
					new UserMessage(toPromptInput(
						trustedCalculation, focusArea, question
					))
				),
				options(targetYear, focusArea, trustedCalculation)
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
			resultValidator.validate(result, targetYear, focusArea, trustedCalculation);
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
		return objectMapper.writeValueAsString(message);
	}

	private OpenAiChatOptions options(
		int targetYear,
		String focusArea,
		Map<String, Object> trustedCalculation
	) {
		return OpenAiChatOptions.builder()
			.model(model)
			.maxCompletionTokens(MAX_COMPLETION_TOKENS)
			.temperature(1d)
			.responseFormat(ResponseFormat.builder()
				.type(ResponseFormat.Type.JSON_SCHEMA)
				.jsonSchema(ResponseFormat.JsonSchema.builder()
					.name("saju_birth_annual_question")
					.strict(true)
					.schema(ReadingResponseSchema.wrap(readingResultSchema(
						targetYear,
						focusArea,
						resultValidator.availableEvidenceKeys(trustedCalculation)
					)))
					.build())
				.build())
			.build();
	}

	Map<String, Object> readingResultSchema(
		int targetYear,
		String focusArea,
		List<String> evidenceKeys
	) {
		Map<String, Object> evidence = Map.of(
			"type", "array",
			"minItems", 1,
			"items", Map.of("type", "string", "enum", evidenceKeys)
		);
		Map<String, Object> section = Map.of(
			"type", "object",
			"additionalProperties", false,
			"required", List.of("id", "heading", "body", "evidenceKeys"),
			"properties", Map.of(
				"id", Map.of("type", "string", "enum", SajuReadingResultValidator.SECTION_IDS),
				"heading", textSchema(),
				"body", textSchema(),
				"evidenceKeys", evidence
			)
		);
		return Map.of(
			"type", "object",
			"additionalProperties", false,
			"required", List.of(
				"readingMode", "title", "summary", "natalSections", "annualReading",
				"questionReading", "guidance", "disclaimer"
			),
			"properties", Map.of(
				"readingMode", Map.of(
					"type", "string",
					"enum", java.util.Arrays.stream(com.myeongro.api.domain.reading.service.ReadingMode.values())
						.map(com.myeongro.api.domain.reading.service.ReadingMode::value)
						.toList()
				),
				"title", textSchema(),
				"summary", textSchema(),
				"natalSections", Map.of(
					"type", "array", "minItems", 4, "maxItems", 4, "items", section
				),
				"annualReading", Map.of(
					"type", "object", "additionalProperties", false,
					"required", List.of("year", "heading", "body", "evidenceKeys"),
					"properties", Map.of(
						"year", Map.of("type", "integer", "enum", List.of(targetYear)),
						"heading", textSchema(), "body", textSchema(), "evidenceKeys", evidence
					)
				),
				"questionReading", Map.of(
					"type", "object", "additionalProperties", false,
					"required", List.of("focusArea", "heading", "body", "evidenceKeys"),
					"properties", Map.of(
						"focusArea", Map.of("type", "string", "enum", List.of(focusArea)),
						"heading", textSchema(), "body", textSchema(), "evidenceKeys", evidence
					)
				),
				"guidance", Map.of(
					"type", "array", "minItems", 1, "maxItems", 1, "items", textSchema()
				),
				"disclaimer", textSchema()
			)
		);
	}

	private Map<String, Object> textSchema() {
		return Map.of("type", "string", "minLength", 1);
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
