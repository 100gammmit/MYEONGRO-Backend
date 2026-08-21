package com.myeongro.api.domain.readingcredit.dto;

import java.time.Instant;
import java.util.Map;

public record ReadingCreditStatusResponse(
	int dailyFreeGrant,
	Balance balance,
	Instant nextResetAt,
	boolean generationInProgress,
	Costs costs
) {
	public record Balance(int free, int paid, int total) {
		public static Balance of(int free, int paid) {
			return new Balance(free, paid, free + paid);
		}
	}

	public record Costs(Map<String, Integer> tarot, int saju) {
	}
}
