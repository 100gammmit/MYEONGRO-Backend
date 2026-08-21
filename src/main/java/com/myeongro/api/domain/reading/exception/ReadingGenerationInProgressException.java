package com.myeongro.api.domain.reading.exception;

public class ReadingGenerationInProgressException extends RuntimeException {

	public ReadingGenerationInProgressException() {
		super("이미 생성 중인 리딩이 있습니다.");
	}
}
