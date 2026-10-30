package com.myeongro.api.domain.reading.exception;

public class ReadingGenerationExpiredException extends RuntimeException {

	public ReadingGenerationExpiredException() {
		super("리딩 생성이 만료되었습니다. 다시 시도해 주세요.");
	}
}
