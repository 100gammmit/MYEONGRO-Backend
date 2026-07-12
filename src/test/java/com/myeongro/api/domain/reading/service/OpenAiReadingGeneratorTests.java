package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
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
import org.springframework.core.io.ByteArrayResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;

class OpenAiReadingGeneratorTests {

	private static final String STRUCTURED_RESPONSE = """
		{
		  "title": "흐름을 현실로 옮기는 방법",
		  "summary": "지금까지의 흐름을 살피고 작은 행동으로 옮겨 보세요.",
		  "sections": [
		    {
		      "heading": "과거 - 바보",
		      "body": "새로운 가능성이 출발점이 되었습니다."
		    },
		    {
		      "heading": "현재 - 마법사",
		      "body": "가진 자원을 활용할 시점입니다."
		    },
		    {
		      "heading": "조언 - 힘",
		      "body": "서두르지 말고 꾸준히 움직여 보세요."
		    }
		  ],
		  "guidance": ["오늘 할 작은 행동을 정해 보세요.", "사용 가능한 자원을 적어 보세요."],
		  "disclaimer": "이 리딩은 오락과 자기 성찰을 위한 참고입니다."
		}
		""";
	private static final String SYSTEM_PROMPT = """
		You are MYEONGRO's Korean tarot reading generator.
		Return only valid JSON.
		""";

	@Test
	void sendsReadingInputWithJsonSchemaResponseFormat() {
		CapturingChatModel chatModel = new CapturingChatModel(STRUCTURED_RESPONSE);
		OpenAiReadingGenerator generator = new OpenAiReadingGenerator(
			chatModel,
			new ObjectMapper(),
			"gpt-test",
			1200,
			new ByteArrayResource(SYSTEM_PROMPT.getBytes(StandardCharsets.UTF_8)),
			new TarotReadingResultValidator()
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

		assertThat(result.title()).isEqualTo("흐름을 현실로 옮기는 방법");
		assertThat(chatModel.prompt).isNotNull();
		assertThat(chatModel.prompt.getSystemMessage())
			.extracting(SystemMessage::getText)
			.asString()
			.isEqualTo(SYSTEM_PROMPT);
		assertThat(chatModel.prompt.getUserMessage())
			.extracting(UserMessage::getText)
			.asString()
			.contains("\"kind\":\"tarot\"")
			.contains("\"question\":\"How is today?\"")
			.contains("\"tier\":\"free\"");

		assertThat(chatModel.prompt.getOptions()).isInstanceOf(OpenAiChatOptions.class);
		OpenAiChatOptions options = (OpenAiChatOptions) chatModel.prompt.getOptions();
		assertThat(options.getModel()).isEqualTo("gpt-test");
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(1200);
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
		@SuppressWarnings("unchecked")
		Map<String, Object> sections = (Map<String, Object>) properties.get("sections");
		assertThat(sections)
			.containsEntry("minItems", 3)
			.containsEntry("maxItems", 3);
		@SuppressWarnings("unchecked")
		Map<String, Object> guidance = (Map<String, Object>) properties.get("guidance");
		assertThat(guidance)
			.containsEntry("minItems", 2)
			.containsEntry("maxItems", 3);
		assertThat(options.getResponseFormat().getJsonSchema().getStrict()).isTrue();
	}

	@Test
	void rejectsStructurallyValidResponseThatViolatesTarotContract() {
		String invalidResponse = """
			{
			  "title": "제목",
			  "summary": "요약",
			  "sections": [
			    {"heading": "과거", "body": "본문"}
			  ],
			  "guidance": ["제안"],
			  "disclaimer": "안내"
			}
			""";
		OpenAiReadingGenerator generator = new OpenAiReadingGenerator(
			new CapturingChatModel(invalidResponse),
			new ObjectMapper(),
			"gpt-test",
			1200,
			new ByteArrayResource(SYSTEM_PROMPT.getBytes(StandardCharsets.UTF_8)),
			new TarotReadingResultValidator()
		);

		assertThatThrownBy(() -> generator.generate(
			ReadingKind.TAROT,
			"오늘의 흐름은?",
			Map.of("question", "오늘의 흐름은?", "cards", List.of())
		))
			.isInstanceOf(OpenAiReadingGenerationException.class);
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
			1200,
			new ByteArrayResource(SYSTEM_PROMPT.getBytes(StandardCharsets.UTF_8)),
			new TarotReadingResultValidator()
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
