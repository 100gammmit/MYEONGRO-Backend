package com.myeongro.api.domain.readingcredit.repository;

public record ReadingCreditSnapshot(
	int freeBalance,
	int paidBalance,
	boolean generationInProgress
) {
}
