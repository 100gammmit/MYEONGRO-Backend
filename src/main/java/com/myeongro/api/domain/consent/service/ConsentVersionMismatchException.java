package com.myeongro.api.domain.consent.service;

public class ConsentVersionMismatchException extends RuntimeException {

	public ConsentVersionMismatchException() {
		super("동의 문서가 변경되었습니다. 최신 내용을 다시 확인해 주세요.");
	}
}
