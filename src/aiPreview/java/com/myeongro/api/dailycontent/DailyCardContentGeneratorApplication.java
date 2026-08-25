package com.myeongro.api.dailycontent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.tarot.model.MajorArcana;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;
import com.myeongro.api.domain.tarot.service.TarotPromptCatalog;

@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
	DataSourceAutoConfiguration.class,
	FlywayAutoConfiguration.class,
	HibernateJpaAutoConfiguration.class,
	RedisAutoConfiguration.class,
	RedisRepositoriesAutoConfiguration.class,
	SessionAutoConfiguration.class,
	SecurityAutoConfiguration.class
})
@Import(TarotPromptCatalog.class)
public class DailyCardContentGeneratorApplication {

	private static final String CONTENT_VERSION = "daily-one-card-static-v1";
	private static final int VARIANT_COUNT = 6;

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(
			DailyCardContentGeneratorApplication.class
		);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.run(args).close();
	}

	@Bean
	CommandLineRunner generate(
		ChatModel chatModel,
		ObjectMapper objectMapper,
		TarotPromptCatalog promptCatalog,
		@Value("${app.reading.openai.model}") String model,
		@Value("${daily-content.output-dir}") Path outputDirectory,
		@Value("${daily-content.cards:}") String requestedCards
	) {
		return args -> new Generator(
			chatModel, objectMapper, promptCatalog, model, outputDirectory,
			requestedCards
		).run();
	}

	private record Today(String heading, String body) {
	}

	private record Content(
		String cardId,
		int variantIndex,
		String title,
		Today today,
		List<String> guidance,
		String disclaimer
	) {
	}

	private static final class Generator {

		private static final List<String> VARIANT_BRIEFS = List.of(
			"오늘 가장 두드러지는 전체 분위기",
			"일이 진행되거나 지연되는 모습",
			"감정과 자기 태도에서 드러나는 부분",
			"관계와 일상적인 교류에서 드러나는 부분",
			"일과 생활 리듬에서 드러나는 부분",
			"회복하거나 정리할 필요가 있는 부분"
		);
		private static final Pattern COMMAND_LANGUAGE = Pattern.compile(
			"하세요|마세요|하십시오|반드시|해야 합니다|해야 해요"
		);
		private static final Pattern ACTION_HEADING = Pattern.compile(
			"해봐요|나눠봐요|살펴봐요"
		);
		private static final Pattern AWKWARD_PROPOSAL = Pattern.compile("괜찮아요\\?");
		private static final Map<String, String> CARD_NAMES = Map.ofEntries(
			Map.entry("major-00-fool", "바보"),
			Map.entry("major-01-magician", "마법사"),
			Map.entry("major-02-high-priestess", "여사제"),
			Map.entry("major-03-empress", "여제"),
			Map.entry("major-04-emperor", "황제"),
			Map.entry("major-05-hierophant", "교황"),
			Map.entry("major-06-lovers", "연인"),
			Map.entry("major-07-chariot", "전차"),
			Map.entry("major-08-strength", "힘"),
			Map.entry("major-09-hermit", "은둔자"),
			Map.entry("major-10-wheel-of-fortune", "운명의 수레바퀴"),
			Map.entry("major-11-justice", "정의"),
			Map.entry("major-12-hanged-man", "매달린 사람"),
			Map.entry("major-13-death", "죽음"),
			Map.entry("major-14-temperance", "절제"),
			Map.entry("major-15-devil", "악마"),
			Map.entry("major-16-tower", "탑"),
			Map.entry("major-17-star", "별"),
			Map.entry("major-18-moon", "달"),
			Map.entry("major-19-sun", "태양"),
			Map.entry("major-20-judgement", "심판"),
			Map.entry("major-21-world", "세계")
		);

		private final ChatModel chatModel;
		private final ObjectMapper objectMapper;
		private final TarotPromptCatalog promptCatalog;
		private final String model;
		private final Path outputDirectory;
		private final List<String> requestedCards;

		private Generator(
			ChatModel chatModel,
			ObjectMapper objectMapper,
			TarotPromptCatalog promptCatalog,
			String model,
			Path outputDirectory,
			String requestedCards
		) {
			this.chatModel = chatModel;
			this.objectMapper = objectMapper;
			this.promptCatalog = promptCatalog;
			this.model = model;
			this.outputDirectory = outputDirectory;
			this.requestedCards = parseRequestedCards(requestedCards);
		}

		private void run() throws Exception {
			Files.createDirectories(outputDirectory);
			Path contentPath = outputDirectory.resolve(CONTENT_VERSION + ".json");
			List<Content> content = loadExisting(contentPath);
			List<String> targetCards = requestedCards.isEmpty()
				? MajorArcana.all()
				: requestedCards;
			if (!requestedCards.isEmpty()) {
				content.removeIf(item -> requestedCards.contains(item.cardId()));
				writeContent(contentPath, content);
			}
			Set<String> completed = content.stream()
				.map(Content::cardId)
				.collect(java.util.stream.Collectors.toSet());
			for (String cardId : targetCards) {
				if (completed.contains(cardId)) {
					System.out.println("Skipping completed card: " + cardId);
					continue;
				}
				System.out.println("Generating static daily content: " + cardId);
				content.addAll(generateCardWithRetry(cardId));
				writeContent(contentPath, content);
			}
			validateComplete(content);
			writeManifest(outputDirectory.resolve("manifest.json"), content);
			System.out.println("Generated " + content.size() + " static daily-card entries");
		}

		private List<Content> generateCardWithRetry(String cardId) throws Exception {
			IllegalArgumentException lastFailure = null;
			for (int attempt = 1; attempt <= 3; attempt++) {
				try {
					return generateCard(cardId);
				} catch (IllegalArgumentException exception) {
					lastFailure = exception;
					System.out.println("Rejected generated card " + cardId
						+ " on attempt " + attempt + ": " + exception.getMessage());
				}
			}
			throw lastFailure;
		}

		private List<Content> generateCard(String cardId) throws Exception {
			String runtimePrompt = promptCatalog.prompt(
				TarotSpreadType.DAILY_ONE_CARD,
				List.of(cardId)
			);
			String systemPrompt = runtimePrompt + """

				# 오프라인 정적 콘텐츠 생성 작업

				이번 요청에는 사용자 질문이 없으며 안전 판정과 readingMode 분류를 하지 않습니다.
				기존 런타임 출력 형식 대신 JSON Schema에 맞춰 같은 카드의 서로 다른 오늘의 한 장 콘텐츠 6개를 작성하세요.
				각 변형은 제공된 variantBrief 하나만 중심으로 해석하고, 다른 변형과 제목·본문·제안을 되풀이하지 마세요.
				body는 2~3개의 짧고 쉬운 해요체 문장으로 쓰고 카드의 한국어 이름을 정확히 한 번, 관련 상징과 함께 근거로 연결하세요.
				heading은 결론을 압축한 설명형 문구로 쓰고 사용자에게 무엇을 해보거나 나누자고 권하지 마세요.
				body와 heading에서 명령하거나 지시하지 말고 행동 제안은 guidance에만 두세요.
				좋고 나쁜 방향을 알아볼 수 있게 말하되 사건을 만들거나 결과를 보장하지 마세요.
				guidance는 사용자의 선택권을 남기는 따뜻한 제안형 해요체 한 문장, 한 행동만 작성하세요.
				의료·법률·재정 결정이나 위험 행동을 권하지 말고 누구나 일상에서 안전하게 할 수 있는 행동만 제안하세요.
				""";
			String userInput = objectMapper.writeValueAsString(Map.of(
				"cardId", cardId,
				"variantBriefs", VARIANT_BRIEFS
			));
			ChatResponse response = chatModel.call(new Prompt(
				List.of(new SystemMessage(systemPrompt), new UserMessage(userInput)),
				options()
			));
			JsonNode variants = objectMapper.readTree(
				response.getResult().getOutput().getText()
			).required("variants");
			List<Map<String, Object>> raw = objectMapper.convertValue(
				variants, new TypeReference<>() {
				}
			);
			List<Content> generated = new ArrayList<>();
			for (Map<String, Object> item : raw) {
				int index = ((Number) item.get("variantIndex")).intValue();
				generated.add(new Content(
					cardId,
					index,
					requiredText(item, "title"),
					new Today(requiredText(item, "heading"), requiredText(item, "body")),
					List.of(requiredText(item, "guidance")),
					requiredText(item, "disclaimer")
				));
			}
			validateCard(cardId, generated);
			return generated;
		}

		private OpenAiChatOptions options() {
			return OpenAiChatOptions.builder()
				.model(model)
				.maxCompletionTokens(4000)
				.temperature(1d)
				.responseFormat(ResponseFormat.builder()
					.type(ResponseFormat.Type.JSON_SCHEMA)
					.jsonSchema(ResponseFormat.JsonSchema.builder()
						.name("daily_card_static_variants")
						.strict(true)
						.schema(schema())
						.build())
					.build())
				.build();
		}

		private Map<String, Object> schema() {
			Map<String, Object> text = Map.of("type", "string", "minLength", 1);
			Map<String, Object> variant = Map.of(
				"type", "object",
				"additionalProperties", false,
				"required", List.of(
					"variantIndex", "title", "heading", "body", "guidance", "disclaimer"
				),
				"properties", Map.of(
					"variantIndex", Map.of("type", "integer", "enum", List.of(0, 1, 2, 3, 4, 5)),
					"title", text,
					"heading", text,
					"body", text,
					"guidance", text,
					"disclaimer", text
				)
			);
			return Map.of(
				"type", "object",
				"additionalProperties", false,
				"required", List.of("variants"),
				"properties", Map.of("variants", Map.of(
					"type", "array",
					"minItems", VARIANT_COUNT,
					"maxItems", VARIANT_COUNT,
					"items", variant
				))
			);
		}

		private String requiredText(Map<String, Object> item, String key) {
			Object value = item.get(key);
			if (!(value instanceof String text) || text.isBlank()) {
				throw new IllegalArgumentException("Missing static content field: " + key);
			}
			return text.trim();
		}

		private List<Content> loadExisting(Path path) throws Exception {
			if (!Files.exists(path)) {
				return new ArrayList<>();
			}
			return new ArrayList<>(objectMapper.readValue(
				path.toFile(), new TypeReference<List<Content>>() {
				}
			));
		}

		private void writeContent(Path path, List<Content> content) throws Exception {
			content.sort(Comparator.comparing(Content::cardId).thenComparingInt(Content::variantIndex));
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), content);
		}

		private void writeManifest(Path path, List<Content> content) throws Exception {
			Map<String, Object> manifest = new LinkedHashMap<>();
			manifest.put("contentVersion", CONTENT_VERSION);
			manifest.put("model", model);
			manifest.put("promptVersion", promptCatalog.version(TarotSpreadType.DAILY_ONE_CARD));
			manifest.put("generatedAt", Instant.now().toString());
			manifest.put("entryCount", content.size());
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), manifest);
		}

		private void validateComplete(List<Content> content) {
			if (content.size() != MajorArcana.all().size() * VARIANT_COUNT) {
				throw new IllegalArgumentException("Static daily-card content is incomplete");
			}
			for (String cardId : MajorArcana.all()) {
				validateCard(cardId, content.stream()
					.filter(item -> item.cardId().equals(cardId))
					.toList());
			}
		}

		private void validateCard(String cardId, List<Content> content) {
			if (content.size() != VARIANT_COUNT
				|| !content.stream().map(Content::variantIndex).collect(
					java.util.stream.Collectors.toSet()
				).equals(Set.of(0, 1, 2, 3, 4, 5))) {
				throw new IllegalArgumentException("Invalid variants for " + cardId);
			}
			String cardName = CARD_NAMES.get(cardId);
			for (Content item : content) {
				String userText = String.join(" ",
					item.title(), item.today().heading(), item.today().body(),
					item.guidance().getFirst(), item.disclaimer()
				);
				if (occurrences(item.today().body(), cardName) != 1) {
					throw new IllegalArgumentException("Card name evidence is not exactly once");
				}
				if (COMMAND_LANGUAGE.matcher(userText).find()) {
					throw new IllegalArgumentException("Command language is not allowed");
				}
				if (ACTION_HEADING.matcher(item.today().heading()).find()) {
					throw new IllegalArgumentException("Action guidance leaked into heading");
				}
				if (AWKWARD_PROPOSAL.matcher(item.guidance().getFirst()).find()) {
					throw new IllegalArgumentException("Awkward proposal punctuation is not allowed");
				}
			}
		}

		private int occurrences(String text, String target) {
			return text.split(Pattern.quote(target), -1).length - 1;
		}

		private List<String> parseRequestedCards(String value) {
			if (value == null || value.isBlank()) {
				return List.of();
			}
			List<String> cards = java.util.Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(cardId -> !cardId.isEmpty())
				.distinct()
				.toList();
			if (cards.stream().anyMatch(cardId -> !MajorArcana.contains(cardId))) {
				throw new IllegalArgumentException("Unsupported requested daily card ID");
			}
			return cards;
		}
	}
}
