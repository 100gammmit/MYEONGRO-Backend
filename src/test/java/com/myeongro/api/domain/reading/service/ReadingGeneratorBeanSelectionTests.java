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

class ReadingGeneratorBeanSelectionTests {

	private final ApplicationContextRunner contextRunner =
		new ApplicationContextRunner()
			.withPropertyValues(
				"app.reading.openai.model=gpt-test",
				"app.reading.prompts.tarot=classpath:prompts/tarot/major-arcana-3card-ko-v1.md",
				"app.reading.prompts.tarot-version=tarot-prompt-v1"
			)
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.withBean(TarotReadingResultValidator.class)
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

			assertThat(resolver.resolve(ReadingKind.TAROT))
				.isEqualTo(new ReadingGenerationMetadata(
					"openai",
					"gpt-test",
					"tarot-prompt-v1"
				));
			assertThat(resolver.resolve(ReadingKind.SAJU))
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
