package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class OpenAiReadingDiagnosticsTests {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void recordsOnlySafeMetadataAndCorrelatesClassification() throws Exception {
		Logger logger = (Logger)LoggerFactory.getLogger(OpenAiReadingDiagnostics.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			OpenAiChatOptions options = OpenAiChatOptions.builder().model("synthetic-model")
				.maxCompletionTokens(1800).temperature(1d)
				.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA)
					.jsonSchema(ResponseFormat.JsonSchema.builder().name("synthetic").strict(true)
						.schema(ReadingResponseSchema.wrap(Map.of("type", "object"))).build()).build())
				.build();
			Prompt prompt = new Prompt(List.of(new SystemMessage("synthetic-system-secret"),
				new UserMessage("birth-data-and-user-input-secret")), options);
			OpenAiReadingDiagnostics.request("synthetic-id", "tarot", "mind_three_card",
				"https://credential-secret@api.openai.com/path?key=query-secret", prompt,
				"question-secret", mapper);
			OpenAiReadingDiagnostics.classification("synthetic-id", mapper.readTree(
				"{\"resultType\":\"declined\",\"reasonCode\":\"CRISIS_OR_IMMEDIATE_DANGER\"}"));
			OpenAiReadingDiagnostics.classification("synthetic-id", mapper.readTree(
				"{\"resultType\":\"untrusted-secret\",\"reasonCode\":\"response-secret\"}"));
			String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
				.reduce("", (left, right) -> left + "\n" + right);
			assertThat(messages).contains("diagnosticId=synthetic-id", "questionLength=15",
				"configuredBaseUrl=https://api.openai.com", "schemaPropertyOrder=",
				"reasonCode=CRISIS_OR_IMMEDIATE_DANGER", "resultType=invalid");
			assertThat(messages).doesNotContain("synthetic-system-secret", "birth-data-and-user-input-secret",
				"question-secret", "credential-secret", "query-secret", "untrusted-secret", "response-secret");
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	@Test
	void canonicalHashIgnoresMapOrderButRetainsArrayOrder() throws Exception {
		Map<String, Object> first = new LinkedHashMap<>();
		first.put("b", List.of("reading", "declined"));
		first.put("a", Map.of("type", "object"));
		Map<String, Object> second = new LinkedHashMap<>();
		second.put("a", Map.of("type", "object"));
		second.put("b", List.of("reading", "declined"));
		assertThat(mapper.writeValueAsString(OpenAiReadingDiagnostics.canonical(first)))
			.isEqualTo(mapper.writeValueAsString(OpenAiReadingDiagnostics.canonical(second)));
		second.put("b", List.of("declined", "reading"));
		assertThat(mapper.writeValueAsString(OpenAiReadingDiagnostics.canonical(first)))
			.isNotEqualTo(mapper.writeValueAsString(OpenAiReadingDiagnostics.canonical(second)));
	}

	@Test
	void malformedDiagnosticInputDoesNotInterruptGeneration() {
		OpenAiReadingDiagnostics.request("synthetic-id", "tarot", "none", "invalid", null, null, mapper);
		assertThat(OpenAiReadingDiagnostics.safeOrigin("https://user:password@host.test:8443/path?token=secret"))
			.isEqualTo("https://host.test:8443");
		assertThat(OpenAiReadingDiagnostics.safeOrigin("invalid")).isEqualTo("invalid");
	}
}
