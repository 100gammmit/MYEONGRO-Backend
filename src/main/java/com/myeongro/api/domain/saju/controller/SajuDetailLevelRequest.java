package com.myeongro.api.domain.saju.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;

import jakarta.validation.constraints.NotNull;

/** The birth part of an annual report request; the question, focus area and request id play no part in the format. */
public record SajuDetailLevelRequest(
	@NotNull SajuBirthProfileRequest birthProfile,
	/** Optional; absent means the defaults, as on the creation request. */
	JsonNode calculationOptions
) {
	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw InvalidReadingRequestException.unknownField(name);
	}
}
