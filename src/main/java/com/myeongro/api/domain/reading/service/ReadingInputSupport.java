package com.myeongro.api.domain.reading.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

public final class ReadingInputSupport {

	private ReadingInputSupport() {
	}

	public static String normalizeQuestion(String value) {
		if (value == null || value.trim().isEmpty()) {
			throw invalid("QUESTION_REQUIRED", "question", "궁금한 점을 입력해 주세요.");
		}
		String normalized = value.trim();
		if (normalized.length() > 300) {
			throw invalid("QUESTION_TOO_LONG", "question", "질문은 300자 이하로 입력해 주세요.");
		}
		return normalized;
	}

	public static Map<String, Object> hashMaterial(
		ReadingKind kind,
		String spreadType,
		int schemaVersion,
		Map<String, Object> payload
	) {
		return orderedMap(
			"kind", kind.value(),
			"spreadType", spreadType,
			"schemaVersion", schemaVersion,
			"inputPayload", payload
		);
	}

	public static Map<String, Object> orderedMap(Object... entries) {
		Map<String, Object> values = new LinkedHashMap<>();
		for (int index = 0; index < entries.length; index += 2) {
			values.put((String)entries[index], entries[index + 1]);
		}
		return immutable(values);
	}

	public static Map<String, Object> immutable(Map<String, Object> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	public static Map<?, ?> valueAsMap(Object value, String fieldName) {
		if (value instanceof Map<?, ?> map) {
			return map;
		}
		throw new IllegalArgumentException(fieldName + " is invalid");
	}

	public static String valueAsString(Object value, String fieldName) {
		if (value instanceof String text && !text.isBlank()) {
			return text;
		}
		throw new IllegalArgumentException(fieldName + " is invalid");
	}

	public static String nullableString(Object value) {
		return value instanceof String text ? text : null;
	}

	public static InvalidReadingRequestException invalid(
		String code,
		String field,
		String message
	) {
		return new InvalidReadingRequestException(code, field, message);
	}
}
