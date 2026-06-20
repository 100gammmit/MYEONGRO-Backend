package com.myeongro.api.domain.reading.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReadingCreateRequest(
	@NotBlank
	String kind,
	@NotBlank
	String question,
	@NotNull
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

	@JsonAnySetter
	static void rejectUnknownField(String name, Object value) {
		throw new IllegalArgumentException("Unknown field: " + name);
	}
}
