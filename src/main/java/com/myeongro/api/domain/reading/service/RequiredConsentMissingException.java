package com.myeongro.api.domain.reading.service;

public class RequiredConsentMissingException extends RuntimeException {

	public RequiredConsentMissingException(String message) {
		super(message);
	}
}
