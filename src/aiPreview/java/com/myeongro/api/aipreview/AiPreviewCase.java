package com.myeongro.api.aipreview;

import java.util.LinkedHashMap;
import java.util.Map;

import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

record AiPreviewCase(
	String id,
	ReadingKind kind,
	TarotSpreadType spreadType,
	String question,
	Map<String, Object> input
) {

	AiPreviewCase {
		input = input == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(input));
	}
}
