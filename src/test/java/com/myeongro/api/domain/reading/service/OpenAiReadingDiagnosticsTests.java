package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class OpenAiReadingDiagnosticsTests {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void recordsOneCompactEventWithoutResponseText() throws Exception {
		Logger logger = (Logger)LoggerFactory.getLogger(OpenAiReadingDiagnostics.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			OpenAiReadingDiagnostics.classification("tarot", "mind_three_card", options(Map.of("type", "object")),
				mapper.readTree("{\"resultType\":\"declined\",\"reasonCode\":\"CRISIS_OR_IMMEDIATE_DANGER\","
					+ "\"body\":\"response-secret\"}"), mapper, null);
			assertThat(appender.list).hasSize(1);
			assertThat(appender.list.getFirst().getFormattedMessage()).contains(
				"kind=tarot", "spread=mind_three_card", "model=synthetic-model", "schemaSha256=",
				"resultType=declined", "reasonCode=CRISIS_OR_IMMEDIATE_DANGER")
				.doesNotContain("response-secret", "questionLength=");
			assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
			OpenAiReadingDiagnostics.classification("saju", "none", options(Map.of("type", "object")),
				mapper.readTree("{\"resultType\":\"untrusted-secret\",\"reasonCode\":\"response-secret\"}"), mapper, null);
			assertThat(appender.list.getLast().getFormattedMessage()).contains(
				"resultType=invalid", "reasonCode=none-or-invalid")
				.doesNotContain("untrusted-secret", "response-secret");
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	@Test
	void schemaFingerprintPreservesOrder() throws Exception {
		Logger logger = (Logger)LoggerFactory.getLogger(OpenAiReadingDiagnostics.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			Map<String, Object> first = new LinkedHashMap<>();
			first.put("a", "object");
			first.put("b", true);
			Map<String, Object> second = new LinkedHashMap<>();
			second.put("b", true);
			second.put("a", "object");
			var output = mapper.readTree("{\"resultType\":\"reading\"}");
			OpenAiReadingDiagnostics.classification("tarot", "mind_three_card", options(first), output, mapper, null);
			OpenAiReadingDiagnostics.classification("tarot", "mind_three_card", options(first), output, mapper, null);
			OpenAiReadingDiagnostics.classification("tarot", "mind_three_card", options(second), output, mapper, null);
			assertThat(appender.list.get(0).getFormattedMessage())
				.isEqualTo(appender.list.get(1).getFormattedMessage())
				.isNotEqualTo(appender.list.get(2).getFormattedMessage());
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	@Test
	void malformedDiagnosticsDoNotInterruptGeneration() {
		assertThatCode(() -> OpenAiReadingDiagnostics.classification("tarot", "none", null, null, mapper, null))
			.doesNotThrowAnyException();
	}

	@Test
	void logsTokenUsageForSpendTrackingWithoutResponseText() throws Exception {
		Logger logger = (Logger)LoggerFactory.getLogger(OpenAiReadingDiagnostics.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			ChatResponse response = ChatResponse.builder()
				.generations(List.of(new Generation(new AssistantMessage("response-secret"))))
				.metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(120, 45)).build())
				.build();
			OpenAiReadingDiagnostics.classification("tarot", "mind_three_card", options(Map.of("type", "object")),
				mapper.readTree("{\"resultType\":\"reading\"}"), mapper, response);
			assertThat(appender.list.getFirst().getFormattedMessage())
				.contains("promptTokens=120", "completionTokens=45")
				.doesNotContain("response-secret");
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	private OpenAiChatOptions options(Map<String, Object> schema) {
		return OpenAiChatOptions.builder().model("synthetic-model")
			.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA)
				.jsonSchema(ResponseFormat.JsonSchema.builder().name("synthetic").strict(true).schema(schema).build())
				.build()).build();
	}
}
