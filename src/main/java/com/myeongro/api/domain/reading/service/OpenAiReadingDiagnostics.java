package com.myeongro.api.domain.reading.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Application-boundary diagnostics only; never records user input or response text. */
public final class OpenAiReadingDiagnostics {

	private static final Logger log = LoggerFactory.getLogger(OpenAiReadingDiagnostics.class);

	private OpenAiReadingDiagnostics() {
	}

	public static void request(String diagnosticId, String kind, String spread, String baseUrl,
		Prompt prompt, String question, ObjectMapper mapper) {
		try {
			OpenAiChatOptions options = (OpenAiChatOptions)prompt.getOptions();
			Map<String, Object> schema = options.getResponseFormat().getJsonSchema().getSchema();
			String schemaJson = mapper.writeValueAsString(schema);
			List<String> propertyOrder = new ArrayList<>();
			collectPropertyOrder(mapper.readTree(schemaJson), "$", propertyOrder);
			log.info("AI reading diagnostic request: diagnosticId={}, kind={}, spread={}, "
				+ "configuredBaseUrl={}, model={}, maxCompletionTokens={}, temperature={}, "
				+ "messageRoles={}, systemSha256={}, schemaSha256={}, schemaCanonicalSha256={}, "
				+ "schemaPropertyOrder={}, questionPresent={}, questionLength={}",
				diagnosticId, kind, spread, safeOrigin(baseUrl), options.getModel(),
				options.getMaxCompletionTokens(), options.getTemperature(),
				prompt.getInstructions().stream().map(message -> message.getMessageType().name()).toList(),
				sha256(prompt.getSystemMessage().getText()), sha256(schemaJson),
				sha256(mapper.writeValueAsString(canonical(schema))), propertyOrder,
				question != null && !question.isBlank(), question == null ? 0 : question.length());
		} catch (Exception exception) {
			// Diagnostics must never change the generation path, even if serialization fails.
			log.info("AI reading diagnostic unavailable: diagnosticId={}, stage=request, causeType={}",
				diagnosticId, exception.getClass().getSimpleName());
		}
	}

	public static void response(String diagnosticId, OpenAiResponseDiagnostics diagnostics) {
		log.info("AI reading diagnostic response: diagnosticId={}, generationCount={}, finishReason={}, "
			+ "contentPresent={}, contentLength={}, completionTokens={}", diagnosticId,
			diagnostics.generationCount(), diagnostics.finishReason(), diagnostics.contentPresent(),
			diagnostics.contentLength(), diagnostics.completionTokens());
	}

	public static void classification(String diagnosticId, JsonNode output) {
		String resultType = output.path("resultType").asText();
		String reasonCode = output.path("reasonCode").asText();
		log.info("AI reading diagnostic classification: diagnosticId={}, resultType={}, reasonCode={}",
			diagnosticId, List.of("reading", "declined").contains(resultType) ? resultType : "invalid",
			ReadingDeclineReason.modelSelectableValues().contains(reasonCode) ? reasonCode : "none-or-invalid");
	}

	static String safeOrigin(String baseUrl) {
		try {
			URI uri = URI.create(baseUrl);
			if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null) {
				return "invalid";
			}
			return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
		} catch (RuntimeException exception) {
			return "invalid";
		}
	}

	static String sha256(String value) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
			.digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	static Object canonical(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> sorted = new TreeMap<>();
			map.forEach((key, item) -> sorted.put((String)key, canonical(item)));
			return sorted;
		}
		if (value instanceof List<?> list) {
			return list.stream().map(OpenAiReadingDiagnostics::canonical).toList();
		}
		return value;
	}

	private static void collectPropertyOrder(JsonNode node, String path, List<String> result) {
		if (node.isObject()) {
			node.fields().forEachRemaining(entry -> {
				if ("properties".equals(entry.getKey())) {
					entry.getValue().fieldNames().forEachRemaining(name -> result.add(path + "." + name));
				}
				collectPropertyOrder(entry.getValue(), path + "." + entry.getKey(), result);
			});
		} else if (node.isArray()) {
			for (int index = 0; index < node.size(); index++) {
				collectPropertyOrder(node.get(index), path + "[" + index + "]", result);
			}
		}
	}
}
