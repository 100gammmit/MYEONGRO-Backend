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

class ReadingGeneratorBeanSelectionTests {

	private final ApplicationContextRunner contextRunner =
		new ApplicationContextRunner()
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.withUserConfiguration(TestChatModelConfig.class)
			.withUserConfiguration(DemoReadingGenerator.class)
			.withUserConfiguration(OpenAiReadingGenerator.class);

	@Test
	void usesDemoGeneratorWhenGeneratorPropertyIsMissing() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ReadingGenerator.class);
			assertThat(context.getBean(ReadingGenerator.class))
				.isInstanceOf(DemoReadingGenerator.class);
		});
	}

	@Test
	void usesOpenAiGeneratorWhenOpenAiGeneratorIsConfigured() {
		contextRunner
			.withPropertyValues("app.reading.generator=openai")
			.run(context -> {
				assertThat(context).hasSingleBean(ReadingGenerator.class);
				assertThat(context.getBean(ReadingGenerator.class))
					.isInstanceOf(OpenAiReadingGenerator.class);
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
