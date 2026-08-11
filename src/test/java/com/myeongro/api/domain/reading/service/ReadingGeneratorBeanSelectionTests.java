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
import com.myeongro.api.domain.saju.service.OpenAiSajuReadingGenerator;
import com.myeongro.api.domain.saju.service.SajuReadingResultValidator;

class ReadingGeneratorBeanSelectionTests {

	private final ApplicationContextRunner contextRunner =
		new ApplicationContextRunner()
			.withPropertyValues(
				"app.reading.openai.model=gpt-test",
				"app.reading.prompts.tarot.common=classpath:prompts/tarot/common-ko-v6.md",
				"app.reading.prompts.tarot.cards=classpath:prompts/tarot/arcana/major/major-arcana-ko-v4.md",
				"app.reading.prompts.tarot.spreads.daily-one-card=classpath:prompts/tarot/spreads/daily-one-card/daily-one-card-ko-v4.md",
				"app.reading.prompts.tarot.spreads.mind-three-card=classpath:prompts/tarot/spreads/mind-three-card/mind-three-card-ko-v4.md",
				"app.reading.prompts.tarot.spreads.relationship-three-card=classpath:prompts/tarot/spreads/relationship-three-card/relationship-three-card-ko-v4.md",
				"app.reading.prompts.tarot.spreads.choice-five-card=classpath:prompts/tarot/spreads/choice-five-card/choice-five-card-ko-v4.md",
				"app.reading.prompts.saju.common=classpath:prompts/saju/common-ko-v1.md",
				"app.reading.prompts.saju.interpretation=classpath:prompts/saju/interpretation/interpretation-guide-ko-v1.md",
				"app.reading.prompts.saju.reports.birth-annual-question=classpath:prompts/saju/reports/birth-annual-question/birth-annual-question-ko-v1.md"
			)
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.withBean(TarotReadingResultValidator.class)
			.withBean(SajuReadingResultValidator.class)
			.withBean(DeclinedReadingFactory.class)
			.withUserConfiguration(TarotPromptCatalog.class)
			.withUserConfiguration(SajuPromptCatalog.class)
			.withUserConfiguration(TestChatModelConfig.class)
			.withUserConfiguration(DemoReadingGenerator.class)
			.withUserConfiguration(OpenAiReadingGenerator.class)
			.withUserConfiguration(OpenAiSajuReadingGenerator.class)
			.withUserConfiguration(ReadingGeneratorRouter.class)
			.withUserConfiguration(ReadingGenerationMetadataResolver.class);

	@Test
	void exposesRouterAsPrimaryReadingGenerator() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBeansOfType(ReadingGenerator.class)).hasSize(4);
			assertThat(context.getBean(ReadingGenerator.class))
				.isInstanceOf(ReadingGeneratorRouter.class);
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
