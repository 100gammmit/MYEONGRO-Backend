package com.myeongro.api.domain.consent.entity;

import java.util.List;

public enum ConsentScope {

	TAROT("tarot", List.of(
		ConsentDocumentType.TERMS,
		ConsentDocumentType.AI_OVERSEAS_TRANSFER
	)),
	SAJU("saju", List.of(
		ConsentDocumentType.TERMS,
		ConsentDocumentType.AI_OVERSEAS_TRANSFER,
		ConsentDocumentType.SAJU_INPUT
	));

	private final String value;
	private final List<ConsentDocumentType> requiredDocuments;

	ConsentScope(String value, List<ConsentDocumentType> requiredDocuments) {
		this.value = value;
		this.requiredDocuments = requiredDocuments;
	}

	public static ConsentScope fromValue(String value) {
		for (ConsentScope scope : values()) {
			if (scope.value.equals(value)) {
				return scope;
			}
		}
		throw new IllegalArgumentException("Unknown consent scope: " + value);
	}

	public List<ConsentDocumentType> requiredDocuments() {
		return requiredDocuments;
	}

	public String value() {
		return value;
	}
}
