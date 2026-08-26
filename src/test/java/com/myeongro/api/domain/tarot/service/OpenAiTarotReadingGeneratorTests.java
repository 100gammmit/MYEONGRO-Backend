package com.myeongro.api.domain.tarot.service;

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
import com.myeongro.api.domain.reading.dto.GeneratedReading;
import com.myeongro.api.domain.tarot.model.MajorArcana;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.service.DeclinedReadingFactory;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationStage;

class OpenAiTarotReadingGeneratorTests {

	@Test
	void exposesTarotPromptMetadataForTheSelectedSpread() {
		var metadata = generator(new CapturingChatModel("{}"))
			.metadata(TarotSpreadType.MIND_THREE_CARD.value());

		assertThat(metadata.provider()).isEqualTo("openai");
		assertThat(metadata.model()).isEqualTo("gpt-test");
		assertThat(metadata.promptVersion())
			.isEqualTo("common-ko-v6+major-arcana-ko-v4+mind-three-card-ko-v4");
	}

	@ParameterizedTest
	@EnumSource(TarotSpreadType.class)
	void buildsSpreadSpecificSchemaAndCompletionLimit(TarotSpreadType spread) throws Exception {
		CapturingChatModel chatModel = new CapturingChatModel(response(spread));
		OpenAiTarotReadingGenerator generator = generator(chatModel);

		GeneratedReading generated = generator.generate(
			ReadingKind.TAROT, spread.value(), "질문", input(spread)
		);
		assertThat(generated.payload()).containsOnlyKeys(
			"readingMode", "title", "summary", "sections", "guidance", "disclaimer"
		);

		OpenAiChatOptions options = (OpenAiChatOptions) chatModel.prompt.getOptions();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(spread.maxOutputTokens());
		assertThat(options.getResponseFormat().getJsonSchema().getSchema().toString())
			.contains("HIGH_STAKES_DECISION")
			.doesNotContain("MEDICAL_DECISION", "LEGAL_DECISION", "FINANCIAL_DECISION");
		Map<String, Object> schema = readingSchema(options);
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
		assertThat(((Map<?, ?>)properties.get("readingMode")).get("enum"))
			.isEqualTo(List.of(
				"standard", "health_fortune", "money_fortune",
				"relationship_fortune", "career_life_fortune"
			));
		@SuppressWarnings("unchecked")
		Map<String, Object> sections = (Map<String, Object>) properties.get("sections");
		assertThat(sections)
			.containsEntry("minItems", spread.cardCount())
			.containsEntry("maxItems", spread.cardCount());
		@SuppressWarnings("unchecked")
		Map<String, Object> guidance = (Map<String, Object>) properties.get("guidance");
		assertThat(guidance)
			.containsEntry("minItems", 1)
			.containsEntry("maxItems", 1);
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
			{"output":{"resultType":"reading","reading":{"readingMode":"standard","title":"제목","summary":"요약","sections":[
			{"position":"underlying_need","heading":"욕구","body":"본문"},
			{"position":"emotion","heading":"감정","body":"본문"},
			{"position":"self_action","heading":"행동","body":"본문"}],
			"guidance":["하나"],"disclaimer":"안내"}}}
			""";
		assertThatThrownBy(() -> generator(new CapturingChatModel(invalid)).generate(
			ReadingKind.TAROT,
			TarotSpreadType.MIND_THREE_CARD.value(),
			"질문",
			input(TarotSpreadType.MIND_THREE_CARD)
		)).isInstanceOfSatisfying(OpenAiReadingGenerationException.class, exception ->
			assertThat(exception.getStage()).isEqualTo(OpenAiReadingGenerationStage.RESPONSE_CONTRACT)
		);
	}

	@Test
	void identifiesProviderAndResponseParseFailureStages() {
		assertThatThrownBy(() -> generator(new ThrowingChatModel()).generate(
			ReadingKind.TAROT,
			TarotSpreadType.MIND_THREE_CARD.value(),
			"질문",
			input(TarotSpreadType.MIND_THREE_CARD)
		)).isInstanceOfSatisfying(OpenAiReadingGenerationException.class, exception -> {
			assertThat(exception.getStage()).isEqualTo(OpenAiReadingGenerationStage.PROVIDER_CALL);
			assertThat(exception.getCauseType()).isEqualTo("IllegalStateException");
		});

		assertThatThrownBy(() -> generator(new CapturingChatModel("not-json")).generate(
			ReadingKind.TAROT,
			TarotSpreadType.MIND_THREE_CARD.value(),
			"질문",
			input(TarotSpreadType.MIND_THREE_CARD)
		)).isInstanceOfSatisfying(OpenAiReadingGenerationException.class, exception ->
			assertThat(exception.getStage()).isEqualTo(OpenAiReadingGenerationStage.RESPONSE_PARSE)
		);
	}

	@Test
	void returnsServerOwnedDeclineResultWithoutTarotSections() {
		OpenAiTarotReadingGenerator generator = generator(new CapturingChatModel("""
			{"output":{"resultType":"declined","reasonCode":"HIGH_STAKES_DECISION"}}
			"""));

		GeneratedReading generated = generator.generate(
			ReadingKind.TAROT,
			TarotSpreadType.CHOICE_FIVE_CARD.value(),
			"전 재산을 투자할까요?",
			input(TarotSpreadType.CHOICE_FIVE_CARD)
		);

		assertThat(generated.payload())
			.containsEntry("resultType", "declined")
			.containsEntry("reasonCode", "HIGH_STAKES_DECISION")
			.doesNotContainKeys("sections", "summary");
	}

	private OpenAiTarotReadingGenerator generator(ChatModel chatModel) {
		return new OpenAiTarotReadingGenerator(
			chatModel,
			new ObjectMapper(),
			"gpt-test",
			catalog(),
			new TarotReadingResultValidator(),
			new DeclinedReadingFactory()
		);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readingSchema(OpenAiChatOptions options) {
		Map<String, Object> root = options.getResponseFormat().getJsonSchema().getSchema();
		Map<String, Object> rootProperties = (Map<String, Object>)root.get("properties");
		Map<String, Object> output = (Map<String, Object>)rootProperties.get("output");
		List<Map<String, Object>> branches = (List<Map<String, Object>>)output.get("anyOf");
		Map<String, Object> readingProperties =
			(Map<String, Object>)branches.get(0).get("properties");
		return (Map<String, Object>)readingProperties.get("reading");
	}

	private TarotPromptCatalog catalog() {
		return new TarotPromptCatalog(
			new ClassPathResource("prompts/tarot/common-ko-v6.md"),
			new ClassPathResource("prompts/tarot/arcana/major/major-arcana-ko-v4.md"),
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
		return """
			{"output":{"resultType":"reading","reading":{"readingMode":"standard","title":"제목","summary":"요약","sections":[%s],
			"guidance":["작은 행동"],"disclaimer":"오락과 자기 성찰을 위한 참고입니다."}}}
			""".formatted(sections);
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

	private static class ThrowingChatModel implements ChatModel {

		@Override
		public ChatResponse call(Prompt prompt) {
			throw new IllegalStateException("provider unavailable");
		}
	}
}
