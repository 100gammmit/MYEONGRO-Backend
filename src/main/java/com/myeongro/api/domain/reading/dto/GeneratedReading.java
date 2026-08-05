package com.myeongro.api.domain.reading.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record GeneratedReading(String title, Map<String, Object> payload) {

	public GeneratedReading {
		payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
	}
}
