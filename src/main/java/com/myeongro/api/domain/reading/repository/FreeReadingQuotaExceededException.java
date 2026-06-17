package com.myeongro.api.domain.reading.repository;

public class FreeReadingQuotaExceededException extends RuntimeException {

	public FreeReadingQuotaExceededException() {
		super("무료 리딩 이용 한도를 초과했습니다.");
	}
}
