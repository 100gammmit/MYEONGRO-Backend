package com.myeongro.api.domain.reading.exception;

public class ReadingRetryNotAllowedException extends RuntimeException {

	public ReadingRetryNotAllowedException() {
		super("Reading is not retryable.");
	}
}
