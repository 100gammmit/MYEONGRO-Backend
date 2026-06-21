package com.myeongro.api.domain.reading.exception;

public class RequiredConsentMissingException extends RuntimeException {

	public RequiredConsentMissingException(String message) {
		super(message);
	}
}
