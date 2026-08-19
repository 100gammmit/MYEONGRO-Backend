package com.myeongro.api.domain.tarot.service;

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
import com.myeongro.api.domain.tarot.model.TarotSpreadType;
import com.myeongro.api.domain.tarot.result.TarotReadingResult;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationStage;
import com.myeongro.api.domain.reading.service.DeclinedReadingFactory;
import com.myeongro.api.domain.reading.service.OpenAiResponseDiagnostics;
import com.myeongro.api.domain.reading.service.ReadingDeclineReason;
import com.myeongro.api.domain.reading.service.ReadingGenerationHandler;
import com.myeongro.api.domain.reading.service.ReadingGenerationMetadata;
import com.myeongro.api.domain.reading.service.ReadingMode;
import com.myeongro.api.domain.reading.service.ReadingResponseSchema;

@Component
public class OpenAiTarotReadingGenerator implements ReadingGenerationHandler {

	private static final Logger log = LoggerFactory.getLogger(OpenAiTarotReadingGenerator.class);

	private final ChatModel chatModel;
	private final ObjectMapper objectMapper;
	private final String model;
	private final TarotPromptCatalog promptCatalog;
	private final TarotReadingResultValidator resultValidator;
	private final DeclinedReadingFactory declinedReadingFactory;

	public OpenAiTarotReadingGenerator(
		ChatModel chatModel,
		ObjectMapper objectMapper,
		@Value("${app.reading.openai.model}") String model,
		TarotPromptCatalog promptCatalog,
		TarotReadingResultValidator resultValidator,
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
		return ReadingKind.TAROT;
	}

	@Override
	public ReadingGenerationMetadata metadata(String spreadType) {
		TarotSpreadType spread = TarotSpreadType.fromValue(spreadType);
		return new ReadingGenerationMetadata("openai", model, promptCatalog.version(spread));
	}

	@Override
	public GeneratedReading generate(
		ReadingKind kind,
		String spreadTypeValue,
		String question,
		Map<String, Object> input
	) {
		if (kind != ReadingKind.TAROT || spreadTypeValue == null) {
			throw new IllegalArgumentException("Tarot spread input is required");
		}
		TarotSpreadType spreadType = TarotSpreadType.fromValue(spreadTypeValue);
		Prompt prompt;
		try {
			List<String> cardIds = selectedCardIds(spreadType, input);
			prompt = new Prompt(
				List.of(
					new SystemMessage(promptCatalog.prompt(spreadType, cardIds)),
					new UserMessage(toPromptInput(kind, spreadType, question, input))
				),
				options(spreadType)
			);
		} catch (RuntimeException | JsonProcessingException exception) {
			throw failure(OpenAiReadingGenerationStage.REQUEST_BUILD, spreadType, exception);
		}

		ChatResponse response;
		try {
			response = chatModel.call(prompt);
		} catch (RuntimeException exception) {
			throw failure(OpenAiReadingGenerationStage.PROVIDER_CALL, spreadType, exception);
		}
		OpenAiResponseDiagnostics diagnostics = OpenAiResponseDiagnostics.from(response);

		JsonNode output;
		try {
			String content = response.getResult().getOutput().getText();
			output = objectMapper.readTree(content).required("output");
		} catch (RuntimeException | JsonProcessingException exception) {
			throw failure(
				OpenAiReadingGenerationStage.RESPONSE_PARSE,
				spreadType,
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
				throw new IllegalArgumentException("Unsupported tarot response type");
			}
			TarotReadingResult result = objectMapper.treeToValue(
				output.required("reading"), TarotReadingResult.class
			);
			resultValidator.validate(spreadType, result);
			return new GeneratedReading(
				result.title(),
				objectMapper.convertValue(result, new TypeReference<>() {
				})
			);
		} catch (RuntimeException | JsonProcessingException exception) {
			throw failure(
				OpenAiReadingGenerationStage.RESPONSE_CONTRACT,
				spreadType,
				exception,
				diagnostics
			);
		}
	}

	private OpenAiReadingGenerationException failure(
		OpenAiReadingGenerationStage stage,
		TarotSpreadType spreadType,
		Exception exception
	) {
		return failure(stage, spreadType, exception, OpenAiResponseDiagnostics.unavailable());
	}

	private OpenAiReadingGenerationException failure(
		OpenAiReadingGenerationStage stage,
		TarotSpreadType spreadType,
		Exception exception,
		OpenAiResponseDiagnostics diagnostics
	) {
		log.warn(
			"Tarot reading generation failed: stage={}, model={}, spread={}, causeType={}, "
				+ "generationCount={}, finishReason={}, contentPresent={}, contentLength={}, "
				+ "completionTokens={}",
			stage,
			model,
			spreadType.value(),
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
		ReadingKind kind,
		TarotSpreadType spreadType,
		String question,
		Map<String, Object> input
	) throws JsonProcessingException {
		Map<String, Object> readingInput = new LinkedHashMap<>(input);
		readingInput.remove("question");
		return objectMapper.writeValueAsString(Map.of(
			"kind", kind.value(),
			"spreadType", spreadType.value(),
			"locale", "ko-KR",
			"untrustedUserInput", Map.of(
				"question", question,
				"readingInput", readingInput
			)
		));
	}

	private List<String> selectedCardIds(
		TarotSpreadType spreadType,
		Map<String, Object> input
	) {
		Object value = input.get("cards");
		if (!(value instanceof List<?> cards) || cards.size() != spreadType.cardCount()) {
			throw new IllegalArgumentException("Selected tarot cards do not match spread");
		}
		return cards.stream()
			.map(this::selectedCardId)
			.toList();
	}

	private String selectedCardId(Object value) {
		if (!(value instanceof Map<?, ?> card) || !(card.get("cardId") instanceof String cardId)) {
			throw new IllegalArgumentException("Selected tarot card ID is required");
		}
		return cardId;
	}

	private OpenAiChatOptions options(TarotSpreadType spreadType) {
		return OpenAiChatOptions.builder()
			.model(model)
			.maxCompletionTokens(spreadType.maxOutputTokens())
			.temperature(1d)
			.responseFormat(ResponseFormat.builder()
				.type(ResponseFormat.Type.JSON_SCHEMA)
				.jsonSchema(ResponseFormat.JsonSchema.builder()
					.name("tarot_" + spreadType.value())
					.strict(true)
					.schema(ReadingResponseSchema.wrap(readingResultSchema(spreadType)))
					.build())
				.build())
			.build();
	}

	Map<String, Object> readingResultSchema(TarotSpreadType spreadType) {
		List<String> positionIds = spreadType.positions().stream()
			.map(position -> position.id())
			.toList();
		return Map.of(
			"type", "object",
			"additionalProperties", false,
			"required", List.of("readingMode", "title", "summary", "sections", "guidance", "disclaimer"),
			"properties", Map.of(
				"readingMode", readingModeSchema(),
				"title", textSchema(),
				"summary", textSchema(),
				"sections", Map.of(
					"type", "array",
					"minItems", spreadType.cardCount(),
					"maxItems", spreadType.cardCount(),
					"items", Map.of(
						"type", "object",
						"additionalProperties", false,
						"required", List.of("position", "heading", "body"),
						"properties", Map.of(
							"position", Map.of("type", "string", "enum", positionIds),
							"heading", textSchema(),
							"body", textSchema()
						)
					)
				),
				"guidance", Map.of(
					"type", "array",
					"minItems", spreadType.minGuidanceItems(),
					"maxItems", spreadType.maxGuidanceItems(),
					"items", textSchema()
				),
				"disclaimer", textSchema()
			)
		);
	}

	private Map<String, Object> textSchema() {
		return Map.of("type", "string", "minLength", 1);
	}

	private Map<String, Object> readingModeSchema() {
		return Map.of(
			"type", "string",
			"enum", java.util.Arrays.stream(ReadingMode.values()).map(ReadingMode::value).toList()
		);
	}
}
