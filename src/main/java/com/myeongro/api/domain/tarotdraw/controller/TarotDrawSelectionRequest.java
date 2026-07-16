package com.myeongro.api.domain.tarotdraw.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.constraints.NotBlank;

public record TarotDrawSelectionRequest(@NotBlank String candidateToken) {

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw new IllegalArgumentException("Unknown field: " + name);
	}
}
