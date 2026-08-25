package com.myeongro.api.domain.dailycard.controller;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

public record DailyCardSelectionResponse(
	Selection selection
) {

	public record Selection(
		@JsonFormat(pattern = "yyyy-MM-dd") LocalDate dateKst,
		String cardId,
		int variantIndex,
		String contentVersion
	) {
	}
}
