package com.myeongro.api.domain.dailycard.exception;

public class InvalidDailyCardSelectionException extends IllegalArgumentException {

	private final String code;
	private final String field;

	public InvalidDailyCardSelectionException(String code, String field, String message) {
		super(message);
		this.code = code;
		this.field = field;
	}

	public String getCode() {
		return code;
	}

	public String getField() {
		return field;
	}

	public static InvalidDailyCardSelectionException unknownField(String field) {
		return new InvalidDailyCardSelectionException(
			"UNKNOWN_FIELD", field, "지원하지 않는 입력 항목입니다."
		);
	}
}
