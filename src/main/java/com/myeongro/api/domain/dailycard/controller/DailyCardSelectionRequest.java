package com.myeongro.api.domain.dailycard.controller;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.myeongro.api.domain.dailycard.exception.InvalidDailyCardSelectionException;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DailyCardSelectionRequest(
	@NotNull UUID drawId,
	@Min(1) @Max(5) int selectedSlot
) {

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw InvalidDailyCardSelectionException.unknownField(name);
	}
}
