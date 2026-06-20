package com.myeongro.api.domain.consent.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ConsentDocumentType {

	TERMS("terms"),
	PRIVACY("privacy"),
	SENSITIVE_DATA("sensitive-data");

	private static final List<ConsentDocumentType> REQUIRED = List.of(values());

	private final String value;

	ConsentDocumentType(String value) {
		this.value = value;
	}

	@JsonCreator
	public static ConsentDocumentType fromValue(String value) {
		for (ConsentDocumentType type : values()) {
			if (type.value.equals(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown consent document type: " + value);
	}

	public static List<ConsentDocumentType> required() {
		return REQUIRED;
	}

	@JsonValue
	public String value() {
		return value;
	}
}
