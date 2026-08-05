package com.myeongro.api.domain.saju.calculation;

public class SajuCalculationException extends RuntimeException {

	public static final String CODE = "SAJU_CALCULATION_FAILED";

	public SajuCalculationException(Throwable cause) {
		super("사주 계산을 완료하지 못했습니다.", cause);
	}

	public String getCode() {
		return CODE;
	}
}
