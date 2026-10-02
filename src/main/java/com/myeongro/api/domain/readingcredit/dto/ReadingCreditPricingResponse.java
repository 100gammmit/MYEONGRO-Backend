package com.myeongro.api.domain.readingcredit.dto;

// The published price list: the daily free grant and per-reading costs, with no user data.
public record ReadingCreditPricingResponse(
	int dailyFreeGrant,
	ReadingCreditStatusResponse.Costs costs
) {
}
