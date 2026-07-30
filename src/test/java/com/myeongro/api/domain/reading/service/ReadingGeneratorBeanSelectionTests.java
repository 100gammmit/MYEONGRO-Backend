package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

class ReadingGeneratorBeanSelectionTests {

	private final ApplicationContextRunner contextRunner =
		new ApplicationContextRunner()
			.withPropertyValues(
				"app.reading.openai.model=gpt-test",
				"app.reading.prompts.tarot.common=classpath:prompts/tarot/common-ko-v5.md",
				"app.reading.prompts.tarot.cards=classpath:prompts/tarot/major-arcana-ko-v3.md",
				"app.reading.prompts.tarot.spreads.daily-one-card=classpath:prompts/tarot/spreads/daily-one-card-ko-v4.md",
				"app.reading.prompts.tarot.spreads.mind-three-card=classpath:prompts/tarot/spreads/mind-three-card-ko-v4.md",
				"app.reading.prompts.tarot.spreads.relationship-three-card=classpath:prompts/tarot/spreads/relationship-three-card-ko-v4.md",
				"app.reading.prompts.tarot.spreads.choice-five-card=classpath:prompts/tarot/spreads/choice-five-card-ko-v4.md"
			)
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.withBean(TarotReadingResultValidator.class)
			.withUserConfiguration(TarotPromptCatalog.class)
			.withUserConfiguration(TestChatModelConfig.class)
			.withUserConfiguration(DemoReadingGenerator.class)
			.withUserConfiguration(OpenAiReadingGenerator.class)
			.withUserConfiguration(ReadingGeneratorRouter.class)
			.withUserConfiguration(ReadingGenerationMetadataResolver.class);

	@Test
	void exposesRouterAsPrimaryReadingGenerator() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBeansOfType(ReadingGenerator.class)).hasSize(3);
			assertThat(context.getBean(ReadingGenerator.class))
				.isInstanceOf(ReadingGeneratorRouter.class);
		});
	}

	@Test
	void exposesKindSpecificGenerationMetadata() {
		contextRunner.run(context -> {
			ReadingGenerationMetadataResolver resolver =
				context.getBean(ReadingGenerationMetadataResolver.class);

			assertThat(resolver.resolve(
				ReadingKind.TAROT, TarotSpreadType.DAILY_ONE_CARD
			))
				.isEqualTo(new ReadingGenerationMetadata(
					"openai",
					"gpt-test",
					"common-ko-v5+major-arcana-ko-v3+daily-one-card-ko-v4"
				));
			assertThat(resolver.resolve(ReadingKind.SAJU, null))
				.isEqualTo(new ReadingGenerationMetadata(
					"demo",
					"deterministic-demo",
					"demo-saju-v1"
				));
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class TestChatModelConfig {

		@Bean
		ChatModel chatModel() {
			return new ChatModel() {
				@Override
				public ChatResponse call(Prompt prompt) {
					return new ChatResponse(List.of(
						new Generation(new AssistantMessage("{}"))
					));
				}
			};
		}
	}
}
