package com.myeongro.api.global.auth.oauth;

public interface SignupAttemptCoordinator {

	enum AttemptState {
		PENDING,
		COMPLETING,
		COMPLETED,
		CANCELLING,
		CANCELLED,
		MISSING
	}

	enum CompletionClaim {
		ACQUIRED,
		ALREADY_COMPLETED,
		REJECTED
	}

	String beginAttempt();

	AttemptState state(String attemptId);

	CompletionClaim claimCompletion(String attemptId);

	boolean claimCancellation(String attemptId);

	void markCompleted(String attemptId);

	void releaseCompletion(String attemptId);

	void markCancelled(String attemptId);
}
