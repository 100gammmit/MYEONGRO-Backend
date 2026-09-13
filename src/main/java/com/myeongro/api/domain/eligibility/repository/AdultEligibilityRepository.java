package com.myeongro.api.domain.eligibility.repository;

import java.time.Instant;
import java.util.UUID;

public interface AdultEligibilityRepository {

	boolean hasConfirmation(UUID userId);

	boolean hasSignupConfirmation(UUID userId, String signupGenerationId);

	void saveConfirmation(
		UUID userId,
		String policyVersion,
		Instant confirmedAt,
		String method,
		String signupGenerationId
	);
}
