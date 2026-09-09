package com.myeongro.api.domain.eligibility.repository;

import java.time.Instant;
import java.util.UUID;

public interface AdultEligibilityRepository {

	void saveConfirmation(
		UUID userId,
		String policyVersion,
		Instant confirmedAt,
		String method
	);
}
