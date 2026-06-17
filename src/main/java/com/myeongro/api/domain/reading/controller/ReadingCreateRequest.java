package com.myeongro.api.domain.reading.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ReadingCreateRequest(
	String kind,
	String question,
	UUID requestId,
	List<String> cardIds,
	String birthDate,
	String birthTime,
	String gender
) {

	public Map<String, Object> storageInput() {
		if ("tarot".equals(kind)) {
			return Map.of(
				"question", question,
				"cards", cardIds == null ? List.of() : cardIds
			);
		}
		return Map.of(
			"question", question,
			"profile", Map.of(
				"calendarType", "solar",
				"birthDate", birthDate == null ? "" : birthDate,
				"birthTime", birthTime == null ? "" : birthTime,
				"gender", gender == null ? "unspecified" : gender
			)
		);
	}
}
