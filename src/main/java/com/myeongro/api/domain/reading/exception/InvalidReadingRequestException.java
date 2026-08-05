package com.myeongro.api.domain.reading.exception;

public class InvalidReadingRequestException extends IllegalArgumentException {

	private final String code;
	private final String field;

	public InvalidReadingRequestException(String code, String field, String message) {
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

	public static InvalidReadingRequestException unknownField(String field) {
		return new InvalidReadingRequestException(
			"UNKNOWN_FIELD",
			field,
			"지원하지 않는 입력 항목입니다."
		);
	}
}
