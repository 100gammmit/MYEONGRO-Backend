package com.myeongro.api.domain.reading.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;

@Component
public class OpenAiReadingGenerator implements ReadingGenerator {

	private final ChatModel chatModel;
	private final ObjectMapper objectMapper;
	private final String model;
	private final TarotPromptCatalog promptCatalog;
	private final TarotReadingResultValidator resultValidator;
	private final DeclinedReadingFactory declinedReadingFactory;

	public OpenAiReadingGenerator(
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
	public GeneratedReading generate(
		ReadingKind kind,
		TarotSpreadType spreadType,
		String question,
		Map<String, Object> input
	) {
		if (kind != ReadingKind.TAROT || spreadType == null) {
			throw new IllegalArgumentException("Tarot spread input is required");
		}
		try {
			List<String> cardIds = selectedCardIds(spreadType, input);
			ChatResponse response = chatModel.call(new Prompt(
				List.of(
					new SystemMessage(promptCatalog.prompt(spreadType, cardIds)),
					new UserMessage(toPromptInput(kind, spreadType, question, input))
				),
				options(spreadType)
			));
			String content = response.getResult().getOutput().getText();
			JsonNode output = objectMapper.readTree(content).required("output");
			String resultType = output.required("resultType").textValue();
			if ("declined".equals(resultType)) {
				return declinedReadingFactory.create(ReadingDeclineReason.fromValue(
					output.required("reasonCode").textValue()
				));
			}
			if (!"reading".equals(resultType)) {
				throw new IllegalArgumentException("Unsupported tarot response type");
			}
			ReadingResult result = objectMapper.treeToValue(
				output.required("reading"), ReadingResult.class
			);
			resultValidator.validate(spreadType, result);
			return new GeneratedReading(
				result.title(),
				objectMapper.convertValue(result, new TypeReference<>() {
				})
			);
		} catch (RuntimeException | JsonProcessingException exception) {
			throw new OpenAiReadingGenerationException();
		}
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
			"required", List.of("title", "summary", "sections", "guidance", "disclaimer"),
			"properties", Map.of(
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
}
