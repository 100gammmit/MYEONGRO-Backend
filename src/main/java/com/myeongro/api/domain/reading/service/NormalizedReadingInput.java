package com.myeongro.api.domain.reading.service;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

public record NormalizedReadingInput(
	ReadingKind kind,
	TarotSpreadType spreadType,
	int schemaVersion,
	String question,
	Map<String, Object> payload
) {

	public static final int CURRENT_SCHEMA_VERSION = 1;

	public Map<String, Object> hashMaterial() {
		Map<String, Object> material = new LinkedHashMap<>();
		material.put("kind", kind.value());
		material.put("spreadType", spreadType == null ? null : spreadType.value());
		material.put("schemaVersion", schemaVersion);
		material.put("inputPayload", payload);
		return Collections.unmodifiableMap(material);
	}
}
