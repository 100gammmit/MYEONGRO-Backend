package com.myeongro.api.domain.reading.exception;

import java.util.UUID;

public class InsufficientReadingCreditsException extends RuntimeException {

	private final UUID userId;
	private final int required;

	public InsufficientReadingCreditsException(UUID userId, int required) {
		super("리딩 크레딧이 부족합니다.");
		this.userId = userId;
		this.required = required;
	}

	public UUID getUserId() {
		return userId;
	}

	public int getRequired() {
		return required;
	}
}
