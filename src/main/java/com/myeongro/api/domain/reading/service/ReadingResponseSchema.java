package com.myeongro.api.domain.reading.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class ReadingResponseSchema {

	private ReadingResponseSchema() {
	}

	public static Map<String, Object> wrap(Map<String, Object> readingSchema) {
		Map<String, Object> readingBranch = Map.of(
			"type", "object",
			"additionalProperties", false,
			"required", List.of("resultType", "reading"),
			"properties", Map.of(
				"resultType", Map.of("type", "string", "enum", List.of("reading")),
				"reading", readingSchema
			)
		);
		Map<String, Object> declinedBranch = Map.of(
			"type", "object",
			"additionalProperties", false,
			"required", List.of("resultType", "reasonCode"),
			"properties", Map.of(
				"resultType", Map.of("type", "string", "enum", List.of("declined")),
				"reasonCode", Map.of(
					"type", "string",
					"enum", Arrays.stream(ReadingDeclineReason.values())
						.map(Enum::name)
						.toList()
				)
			)
		);
		return Map.of(
			"type", "object",
			"additionalProperties", false,
			"required", List.of("output"),
			"properties", Map.of(
				"output", Map.of("anyOf", List.of(readingBranch, declinedBranch))
			)
		);
	}
}
