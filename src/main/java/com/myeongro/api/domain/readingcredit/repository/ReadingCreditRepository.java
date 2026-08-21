package com.myeongro.api.domain.readingcredit.repository;

import java.time.Duration;
import java.util.UUID;

public interface ReadingCreditRepository {

	ReadingCreditSnapshot getStatus(UUID userId, int dailyFreeGrant);

	int failStaleGenerations(Duration staleAfter);
}
