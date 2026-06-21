package com.myeongro.api.domain.reading.exception;

public class ReadingRecordNotFoundException extends RuntimeException {

	public ReadingRecordNotFoundException() {
		super("Reading was not found.");
	}
}
