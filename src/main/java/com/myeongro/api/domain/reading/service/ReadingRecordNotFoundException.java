package com.myeongro.api.domain.reading.service;

public class ReadingRecordNotFoundException extends RuntimeException {

	public ReadingRecordNotFoundException() {
		super("Reading was not found.");
	}
}
