package com.myeongro.api.domain.reading.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;

@Component
@ConditionalOnProperty(prefix = "app.reading", name = "generator", havingValue = "openai")
public class OpenAiReadingGenerator implements ReadingGenerator {

	private static final String SYSTEM_PROMPT = String.join(" ",
		"You create Korean tarot and saju readings for entertainment and self-reflection.",
		"Never present predictions as certain facts.",
		"Do not provide medical, legal, investment, or crisis instructions.",
		"Return valid JSON that exactly matches the requested schema."
	);

	private final ChatModel chatModel;
	private final ObjectMapper objectMapper;
	private final String model;
	private final int maxOutputTokens;

	public OpenAiReadingGenerator(
		ChatModel chatModel,
		ObjectMapper objectMapper,
		@Value("${app.reading.openai.model:gpt-5.4-mini}") String model,
		@Value("${app.reading.openai.max-output-tokens:1200}") int maxOutputTokens
	) {
		this.chatModel = chatModel;
		this.objectMapper = objectMapper;
		this.model = model;
		this.maxOutputTokens = maxOutputTokens;
	}

	@Override
	public ReadingResult generate(
		ReadingKind kind,
		String question,
		Map<String, Object> input
	) {
		try {
			ChatResponse response = chatModel.call(new Prompt(
				List.of(
					new SystemMessage(SYSTEM_PROMPT),
					new UserMessage(toPromptInput(kind, question, input))
				),
				options()
			));
			String content = response.getResult().getOutput().getText();
			return objectMapper.readValue(content, ReadingResult.class);
		} catch (RuntimeException | JsonProcessingException exception) {
			throw new OpenAiReadingGenerationException();
		}
	}

	private String toPromptInput(
		ReadingKind kind,
		String question,
		Map<String, Object> input
	) throws JsonProcessingException {
		return objectMapper.writeValueAsString(Map.of(
			"kind", kind.value(),
			"tier", "free",
			"locale", "ko-KR",
			"question", question,
			"input", input
		));
	}

	private OpenAiChatOptions options() {
		return OpenAiChatOptions.builder()
			.model(model)
			.maxTokens(maxOutputTokens)
			.temperature(0.7d)
			.responseFormat(ResponseFormat.builder()
				.type(ResponseFormat.Type.JSON_SCHEMA)
				.jsonSchema(ResponseFormat.JsonSchema.builder()
					.name("fortune_reading")
					.strict(true)
					.schema(readingResultSchema())
					.build())
				.build())
			.build();
	}

	private Map<String, Object> readingResultSchema() {
		return Map.of(
			"type", "object",
			"additionalProperties", false,
			"required", List.of(
				"title",
				"summary",
				"sections",
				"guidance",
				"disclaimer"
			),
			"properties", Map.of(
				"title", Map.of("type", "string", "minLength", 1),
				"summary", Map.of("type", "string", "minLength", 1),
				"sections", Map.of(
					"type", "array",
					"minItems", 1,
					"items", Map.of(
						"type", "object",
						"additionalProperties", false,
						"required", List.of("heading", "body"),
						"properties", Map.of(
							"heading", Map.of("type", "string", "minLength", 1),
							"body", Map.of("type", "string", "minLength", 1)
						)
					)
				),
				"guidance", Map.of(
					"type", "array",
					"minItems", 1,
					"items", Map.of("type", "string", "minLength", 1)
				),
				"disclaimer", Map.of("type", "string", "minLength", 1)
			)
		);
	}
}
