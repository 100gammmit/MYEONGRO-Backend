package com.myeongro.api.domain.dailycard.exception;

public class DailyCardContentVersionMismatchException extends RuntimeException {

	public DailyCardContentVersionMismatchException() {
		super("오늘의 한 장 콘텐츠가 갱신되었습니다. 페이지를 새로고침해 주세요.");
	}
}
