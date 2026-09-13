package com.myeongro.api.global.auth.oauth;

public interface SignupAttemptCoordinator {

	record Attempt(String attemptId, String generationId) {
	}

	enum AttemptState {
		PENDING,
		COMPLETING,
		COMPLETED,
		CANCELLING,
		CANCELLED,
		STALE,
		MISSING
	}

	enum CompletionClaim {
		ACQUIRED,
		ALREADY_COMPLETED,
		REJECTED
	}

	Attempt beginAttempt(String provider, String providerUserId);

	AttemptState state(PendingSignupSessionPrincipal principal);

	CompletionClaim claimCompletion(PendingSignupSessionPrincipal principal);

	boolean claimCancellation(PendingSignupSessionPrincipal principal);

	void markCompleted(PendingSignupSessionPrincipal principal);

	void releaseCompletion(PendingSignupSessionPrincipal principal);

	void markCancelled(PendingSignupSessionPrincipal principal);
}
