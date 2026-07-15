package com.myeongro.api.domain.reading.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record ChoiceOptionsRequest(String a, String b) {

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw new IllegalArgumentException("Unknown field: choiceOptions." + name);
	}
}
