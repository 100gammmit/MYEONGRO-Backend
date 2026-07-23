package com.myeongro.api.domain.reading.exception;

public class ReadingIdempotencyConflictException extends RuntimeException {

	public ReadingIdempotencyConflictException() {
		super("같은 요청 ID에 다른 입력을 사용할 수 없습니다.");
	}
}
