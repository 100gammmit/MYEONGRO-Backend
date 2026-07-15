package com.myeongro.api.domain.consent.dto;

import java.time.Instant;

import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEntity;

public record ConsentAcceptance(
	ConsentDocumentType documentType,
	String documentVersion,
	Instant acceptedAt
) {

	public static ConsentAcceptance from(
		ConsentEntity consent,
		ConsentDocumentType documentType
	) {
		return new ConsentAcceptance(
			documentType,
			consent.versionOf(documentType),
			consent.acceptedAtOf(documentType)
		);
	}
}
