package com.myeongro.api.domain.reading.service;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Stable JSON for model input only; does not change the shared mapper or response schema. */
public final class ReadingRequestJson {

	private ReadingRequestJson() {
	}

	public static String write(ObjectMapper objectMapper, Map<String, ?> input)
		throws JsonProcessingException {
		// Sort every map, including maps inside arrays, without reordering the arrays themselves.
		return objectMapper.writer()
			.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.writeValueAsString(input);
	}
}
