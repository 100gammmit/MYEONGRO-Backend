package com.myeongro.api.domain.reading.repository;

public class ReadingRetryNotAllowedException extends RuntimeException {

	public ReadingRetryNotAllowedException() {
		super("Reading is not retryable.");
	}
}
