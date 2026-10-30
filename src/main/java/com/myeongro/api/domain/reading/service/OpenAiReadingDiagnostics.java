package com.myeongro.api.domain.reading.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Removable result diagnostics: one call per generator, no input or response text. */
public final class OpenAiReadingDiagnostics {

	private static final Logger log = LoggerFactory.getLogger(OpenAiReadingDiagnostics.class);

	private OpenAiReadingDiagnostics() {
	}

	public static void classification(String kind, String spread, OpenAiChatOptions options,
		JsonNode output, ObjectMapper mapper, ChatResponse response) {
		if (!log.isInfoEnabled()) {
			return;
		}
		try {
			String schemaJson = mapper.writeValueAsString(options.getResponseFormat().getJsonSchema().getSchema());
			String schemaHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(schemaJson.getBytes(StandardCharsets.UTF_8)));
			String resultType = output.path("resultType").asText();
			String reasonCode = output.path("reasonCode").asText();
			log.info("AI reading classification: kind={}, spread={}, model={}, schemaSha256={}, "
				+ "resultType={}, reasonCode={}, promptTokens={}, completionTokens={}",
				kind, spread, options.getModel(), schemaHash,
				List.of("reading", "declined").contains(resultType) ? resultType : "invalid",
				ReadingDeclineReason.modelSelectableValues().contains(reasonCode) ? reasonCode : "none-or-invalid",
				tokens(response, Usage::getPromptTokens), tokens(response, Usage::getCompletionTokens));
		} catch (Exception exception) {
			// Optional diagnostics must not alter generation or log exception messages.
			log.debug("AI reading classification unavailable: causeType={}", exception.getClass().getSimpleName());
		}
	}

	private static Integer tokens(ChatResponse response, Function<Usage, Integer> field) {
		return response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null
			? null
			: field.apply(response.getMetadata().getUsage());
	}
}
