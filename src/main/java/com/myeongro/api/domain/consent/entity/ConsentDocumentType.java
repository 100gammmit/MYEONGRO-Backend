package com.myeongro.api.domain.consent.entity;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ConsentDocumentType {

	TERMS("terms"),
	AI_OVERSEAS_TRANSFER("ai-overseas-transfer"),
	SAJU_INPUT("saju-input");

	private static final List<ConsentDocumentType> ACTIVE = List.of(
		TERMS,
		AI_OVERSEAS_TRANSFER,
		SAJU_INPUT
	);

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

	public static List<ConsentDocumentType> active() {
		return ACTIVE;
	}

	public boolean canBeWithdrawn() {
		return this == AI_OVERSEAS_TRANSFER;
	}

	@JsonValue
	public String value() {
		return value;
	}
}
