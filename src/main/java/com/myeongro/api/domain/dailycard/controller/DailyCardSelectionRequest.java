package com.myeongro.api.domain.dailycard.controller;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.myeongro.api.domain.dailycard.exception.InvalidDailyCardSelectionException;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DailyCardSelectionRequest(
	@NotNull UUID drawId,
	@Min(1) @Max(5) int selectedSlot,
	@NotBlank
	@Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
	String contentVersion
) {

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw InvalidDailyCardSelectionException.unknownField(name);
	}
}
