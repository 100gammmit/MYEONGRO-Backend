package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;

class OpenAiReadingGeneratorTests {

	private static final String STRUCTURED_RESPONSE = """
		{
		  "title": "Structured reading",
		  "summary": "A balanced summary.",
		  "sections": [
		    {
		      "heading": "Flow",
		      "body": "Start with one small decision."
		    }
		  ],
		  "guidance": ["Check one practical signal before acting."],
		  "disclaimer": "For entertainment and self-reflection only."
		}
		""";

	@Test
	void sendsReadingInputWithJsonSchemaResponseFormat() {
		CapturingChatModel chatModel = new CapturingChatModel(STRUCTURED_RESPONSE);
		OpenAiReadingGenerator generator = new OpenAiReadingGenerator(
			chatModel,
			new ObjectMapper(),
			"gpt-test",
			1200
		);

		ReadingResult result = generator.generate(
			ReadingKind.TAROT,
			"How is today?",
			Map.of(
				"question", "How is today?",
				"cards", List.of(
					Map.of("cardId", "major-00-fool", "position", "past", "reversed", false),
					Map.of("cardId", "major-01-magician", "position", "present", "reversed", false),
					Map.of("cardId", "major-02-high-priestess", "position", "guidance", "reversed", false)
				)
			)
		);

		assertThat(result.title()).isEqualTo("Structured reading");
		assertThat(chatModel.prompt).isNotNull();
		assertThat(chatModel.prompt.getSystemMessage())
			.extracting(SystemMessage::getText)
			.asString()
			.contains("Korean tarot and saju readings")
			.contains("Return valid JSON");
		assertThat(chatModel.prompt.getUserMessage())
			.extracting(UserMessage::getText)
			.asString()
			.contains("\"kind\":\"tarot\"")
			.contains("\"question\":\"How is today?\"")
			.contains("\"tier\":\"free\"");

		assertThat(chatModel.prompt.getOptions()).isInstanceOf(OpenAiChatOptions.class);
		OpenAiChatOptions options = (OpenAiChatOptions) chatModel.prompt.getOptions();
		assertThat(options.getModel()).isEqualTo("gpt-test");
		assertThat(options.getMaxTokens()).isEqualTo(1200);
		assertThat(options.getTemperature()).isEqualTo(0.7d);
		assertThat(options.getResponseFormat().getType())
			.isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
		assertThat(options.getResponseFormat().getJsonSchema().getName())
			.isEqualTo("fortune_reading");
		Map<String, Object> schema = options.getResponseFormat().getJsonSchema()
			.getSchema();
		assertThat(schema).containsEntry("additionalProperties", false);
		assertThat(schema).containsKey("properties");
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
		assertThat(properties).containsKeys("title", "sections");
		assertThat(options.getResponseFormat().getJsonSchema().getStrict()).isTrue();
	}

	@Test
	void hidesProviderErrorsBehindStableGeneratorException() {
		CapturingChatModel chatModel = new CapturingChatModel(
			new IllegalStateException("raw provider detail")
		);
		OpenAiReadingGenerator generator = new OpenAiReadingGenerator(
			chatModel,
			new ObjectMapper(),
			"gpt-test",
			1200
		);

		assertThatThrownBy(() -> generator.generate(
			ReadingKind.TAROT,
			"How is today?",
			Map.of("question", "How is today?", "cards", List.of())
		))
			.isInstanceOf(OpenAiReadingGenerationException.class)
			.hasMessage("OpenAI reading generation failed")
			.hasFieldOrPropertyWithValue(
				"code",
				"OPENAI_READING_GENERATION_FAILED"
			);
	}

	private static class CapturingChatModel implements ChatModel {

		private final String content;
		private final RuntimeException exception;
		private Prompt prompt;

		CapturingChatModel(String content) {
			this.content = content;
			this.exception = null;
		}

		CapturingChatModel(RuntimeException exception) {
			this.content = null;
			this.exception = exception;
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			this.prompt = prompt;
			if (exception != null) {
				throw exception;
			}
			return new ChatResponse(List.of(
				new Generation(new AssistantMessage(content))
			));
		}
	}
}
