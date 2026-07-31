package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.entity.MajorArcana;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;

class OpenAiReadingGeneratorTests {

	@ParameterizedTest
	@EnumSource(TarotSpreadType.class)
	void buildsSpreadSpecificSchemaAndCompletionLimit(TarotSpreadType spread) throws Exception {
		CapturingChatModel chatModel = new CapturingChatModel(response(spread));
		OpenAiReadingGenerator generator = generator(chatModel);

		generator.generate(ReadingKind.TAROT, spread, "질문", input(spread));

		OpenAiChatOptions options = (OpenAiChatOptions) chatModel.prompt.getOptions();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(spread.maxOutputTokens());
		Map<String, Object> schema = options.getResponseFormat().getJsonSchema().getSchema();
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
		@SuppressWarnings("unchecked")
		Map<String, Object> sections = (Map<String, Object>) properties.get("sections");
		assertThat(sections)
			.containsEntry("minItems", spread.cardCount())
			.containsEntry("maxItems", spread.cardCount());
		@SuppressWarnings("unchecked")
		Map<String, Object> sectionItems = (Map<String, Object>) sections.get("items");
		@SuppressWarnings("unchecked")
		Map<String, Object> sectionProperties =
			(Map<String, Object>) sectionItems.get("properties");
		@SuppressWarnings("unchecked")
		Map<String, Object> position = (Map<String, Object>) sectionProperties.get("position");
		assertThat(position.get("enum")).isEqualTo(spread.positions().stream()
			.map(item -> item.id())
			.toList());
		String systemMessage = chatModel.prompt.getSystemMessage().getText();
		assertThat(systemMessage).isNotBlank();
		assertThat(MajorArcana.all().subList(0, spread.cardCount()))
			.allSatisfy(cardId -> assertThat(systemMessage).contains(cardId));
		assertThat(systemMessage).doesNotContain("major-21-world");
		@SuppressWarnings("unchecked")
		Map<String, Object> userMessage = new ObjectMapper().readValue(
			chatModel.prompt.getUserMessage().getText(),
			Map.class
		);
		assertThat(userMessage)
			.containsKey("untrustedUserInput")
			.doesNotContainKey("tier");
		@SuppressWarnings("unchecked")
		Map<String, Object> untrustedInput =
			(Map<String, Object>) userMessage.get("untrustedUserInput");
		assertThat(untrustedInput)
			.containsEntry("question", "질문")
			.containsKey("readingInput");
		assertThat(untrustedInput.get("readingInput")).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> readingInput =
			(Map<String, Object>) untrustedInput.get("readingInput");
		assertThat(readingInput)
			.containsKey("cards")
			.doesNotContainKey("question");
	}

	@Test
	void rejectsResponseWithWrongPositionOrder() {
		String invalid = """
			{"title":"제목","summary":"요약","sections":[
			{"position":"underlying_need","heading":"욕구","body":"본문"},
			{"position":"emotion","heading":"감정","body":"본문"},
			{"position":"self_action","heading":"행동","body":"본문"}],
			"guidance":["하나","둘"],"disclaimer":"안내"}
			""";
		assertThatThrownBy(() -> generator(new CapturingChatModel(invalid)).generate(
			ReadingKind.TAROT,
			TarotSpreadType.MIND_THREE_CARD,
			"질문",
			input(TarotSpreadType.MIND_THREE_CARD)
		)).isInstanceOf(OpenAiReadingGenerationException.class);
	}

	private OpenAiReadingGenerator generator(ChatModel chatModel) {
		return new OpenAiReadingGenerator(
			chatModel,
			new ObjectMapper(),
			"gpt-test",
			catalog(),
			new TarotReadingResultValidator()
		);
	}

	private TarotPromptCatalog catalog() {
		return new TarotPromptCatalog(
			new ClassPathResource("prompts/tarot/common-ko-v5.md"),
			new ClassPathResource("prompts/tarot/arcana/major/major-arcana-ko-v3.md"),
			new ClassPathResource("prompts/tarot/spreads/daily-one-card/daily-one-card-ko-v4.md"),
			new ClassPathResource("prompts/tarot/spreads/mind-three-card/mind-three-card-ko-v4.md"),
			new ClassPathResource("prompts/tarot/spreads/relationship-three-card/relationship-three-card-ko-v4.md"),
			new ClassPathResource("prompts/tarot/spreads/choice-five-card/choice-five-card-ko-v4.md")
		);
	}

	private Map<String, Object> input(TarotSpreadType spread) {
		return Map.of(
			"question", "질문",
			"cards", IntStream.range(0, spread.cardCount())
				.mapToObj(index -> Map.of(
					"cardId", MajorArcana.all().get(index),
					"position", spread.positions().get(index).id(),
					"reversed", false
				))
				.toList()
		);
	}

	private String response(TarotSpreadType spread) {
		String sections = spread.positions().stream()
			.map(position -> """
				{"position":"%s","heading":"%s - 바보","body":"본문"}
				""".formatted(position.id(), position.displayName()).trim())
			.collect(java.util.stream.Collectors.joining(","));
		String guidance = spread == TarotSpreadType.DAILY_ONE_CARD
			? "[\"작은 행동\"]"
			: "[\"행동 하나\",\"행동 둘\"]";
		return """
			{"title":"제목","summary":"요약","sections":[%s],
			"guidance":%s,"disclaimer":"오락과 자기 성찰을 위한 참고입니다."}
			""".formatted(sections, guidance);
	}

	private static class CapturingChatModel implements ChatModel {

		private final String content;
		private Prompt prompt;

		CapturingChatModel(String content) {
			this.content = content;
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			this.prompt = prompt;
			return new ChatResponse(List.of(
				new Generation(new AssistantMessage(content))
			));
		}
	}
}
