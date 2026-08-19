package com.myeongro.api.domain.tarot.controller;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TarotReadingCreateRequest(
	@NotBlank
	String spreadType,
	String question,
	@NotNull
	UUID requestId,
	List<Integer> selectedSlots,
	ChoiceOptionsRequest choiceOptions
) {

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw InvalidReadingRequestException.unknownField(name);
	}
}
