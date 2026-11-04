package com.myeongro.api.domain.saju.controller;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;

import jakarta.validation.constraints.NotNull;

public record SajuReadingCreateRequest(
	String question,
	@NotNull
	UUID requestId,
	@NotNull
	SajuBirthProfileRequest birthProfile,
	String focusArea,
	@com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = TargetYearDeserializer.class)
	Integer targetYear
) {
	public static class TargetYearDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<Integer> {
		@Override
		public Integer getNullValue(com.fasterxml.jackson.databind.DeserializationContext context)
			throws com.fasterxml.jackson.databind.JsonMappingException {
			return context.reportInputMismatch(Integer.class, "targetYear must be an integer when present");
		}

		@Override
		public Integer getAbsentValue(com.fasterxml.jackson.databind.DeserializationContext context) {
			return null;
		}

		@Override
		public Integer deserialize(com.fasterxml.jackson.core.JsonParser parser, com.fasterxml.jackson.databind.DeserializationContext context)
			throws java.io.IOException {
			if (!parser.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT)) {
				return context.reportInputMismatch(Integer.class, "targetYear must be an integer");
			}
			return parser.getIntValue();
		}
	}
	public SajuReadingCreateRequest(String question, UUID requestId, SajuBirthProfileRequest birthProfile, String focusArea) {
		this(question, requestId, birthProfile, focusArea, null);
	}

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw InvalidReadingRequestException.unknownField(name);
	}
}
