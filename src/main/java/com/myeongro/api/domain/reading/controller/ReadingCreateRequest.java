package com.myeongro.api.domain.reading.controller;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReadingCreateRequest(
	@NotBlank
	String kind,
	String spreadType,
	@NotBlank
	String question,
	@NotNull
	UUID requestId,
	String drawSessionId,
	ChoiceOptionsRequest choiceOptions,
	String birthDate,
	String birthTime,
	String gender
) {

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw new IllegalArgumentException("Unknown field: " + name);
	}
}
