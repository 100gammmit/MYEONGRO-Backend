package com.myeongro.api.domain.consent.dto;

import java.time.Instant;

import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEventEntity;

public record ConsentAcceptance(
	ConsentDocumentType documentType,
	String documentVersion,
	Instant acceptedAt
) {

	public static ConsentAcceptance from(ConsentEventEntity event) {
		return new ConsentAcceptance(
			event.getDocumentType(),
			event.getDocumentVersion(),
			event.getOccurredAt()
		);
	}
}
