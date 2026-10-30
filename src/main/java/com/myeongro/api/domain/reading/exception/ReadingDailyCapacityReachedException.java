package com.myeongro.api.domain.reading.exception;

public class ReadingDailyCapacityReachedException extends RuntimeException {

	public ReadingDailyCapacityReachedException() {
		super("오늘 준비된 리딩 생성량을 모두 사용했습니다.");
	}
}
