package com.myeongro.api.domain.reading.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReadingKind {

	TAROT("tarot"),
	SAJU("saju");

	private final String value;

	ReadingKind(String value) {
		this.value = value;
	}

	@JsonCreator
	public static ReadingKind fromValue(String value) {
		for (ReadingKind kind : values()) {
			if (kind.value.equals(value)) {
				return kind;
			}
		}
		throw new IllegalArgumentException("Unknown reading kind: " + value);
	}

	@JsonValue
	public String value() {
		return value;
	}
}
