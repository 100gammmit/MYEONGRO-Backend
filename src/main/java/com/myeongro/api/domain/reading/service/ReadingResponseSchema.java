package com.myeongro.api.domain.reading.service;

import static java.util.Map.entry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReadingResponseSchema {

	private ReadingResponseSchema() {
	}

	// Structured Outputs follows schema key order. Never replace this with Map.of/copyOf.
	@SafeVarargs
	public static Map<String, Object> orderedMap(Map.Entry<String, Object>... entries) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : entries) {
			if (result.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
				throw new IllegalArgumentException("Duplicate schema key: " + entry.getKey());
			}
		}
		return Collections.unmodifiableMap(result);
	}

	public static Map<String, Object> wrap(Map<String, Object> readingSchema) {
		Map<String, Object> readingBranch = orderedMap(
			entry("additionalProperties", false),
			entry("properties", orderedMap(
				entry("reading", readingSchema),
				entry("resultType", orderedMap(entry("enum", List.of("reading")), entry("type", "string")))
			)),
			entry("required", List.of("resultType", "reading")),
			entry("type", "object")
		);
		Map<String, Object> declinedBranch = orderedMap(
			entry("additionalProperties", false),
			entry("properties", orderedMap(
				entry("reasonCode", orderedMap(
					entry("enum", ReadingDeclineReason.modelSelectableValues()), entry("type", "string")
				)),
				entry("resultType", orderedMap(entry("enum", List.of("declined")), entry("type", "string")))
			)),
			entry("required", List.of("resultType", "reasonCode")),
			entry("type", "object")
		);
		return orderedMap(
			entry("additionalProperties", false),
			entry("properties", orderedMap(
				entry("output", orderedMap(entry("anyOf", List.of(readingBranch, declinedBranch))))
			)),
			entry("required", List.of("output")),
			entry("type", "object")
		);
	}
}
