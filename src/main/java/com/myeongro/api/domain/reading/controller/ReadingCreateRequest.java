package com.myeongro.api.domain.reading.controller;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReadingCreateRequest(
	@NotBlank
	String kind,
	String spreadType,
	String question,
	@NotNull
	UUID requestId,
	String drawSessionId,
	ChoiceOptionsRequest choiceOptions,
	SajuBirthProfileRequest birthProfile,
	String focusArea
) {

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw InvalidReadingRequestException.unknownField(name);
	}
}
