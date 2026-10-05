package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
					+ "\"body\":\"response-secret\"}"), mapper);
			assertThat(appender.list).hasSize(1);
			assertThat(appender.list.getFirst().getFormattedMessage()).contains(
				"kind=tarot", "spread=mind_three_card", "model=synthetic-model", "schemaSha256=",
				"resultType=declined", "reasonCode=CRISIS_OR_IMMEDIATE_DANGER")
				.doesNotContain("response-secret", "diagnosticId=", "questionLength=", "schemaPropertyOrder=");
			assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
			OpenAiReadingDiagnostics.classification("saju", "none", options(Map.of("type", "object")),
				mapper.readTree("{\"resultType\":\"untrusted-secret\",\"reasonCode\":\"response-secret\"}"), mapper);
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
			OpenAiReadingDiagnostics.classification("tarot", "mind_three_card", options(first), output, mapper);
			OpenAiReadingDiagnostics.classification("tarot", "mind_three_card", options(first), output, mapper);
			OpenAiReadingDiagnostics.classification("tarot", "mind_three_card", options(second), output, mapper);
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
		assertThatCode(() -> OpenAiReadingDiagnostics.classification("tarot", "none", null, null, mapper))
			.doesNotThrowAnyException();
	}

	private OpenAiChatOptions options(Map<String, Object> schema) {
		return OpenAiChatOptions.builder().model("synthetic-model")
			.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA)
				.jsonSchema(ResponseFormat.JsonSchema.builder().name("synthetic").strict(true).schema(schema).build())
				.build()).build();
	}
}
