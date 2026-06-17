package com.myeongro.api.domain.consent.dto;

import java.util.List;

import com.myeongro.api.domain.consent.entity.ConsentDocumentType;

public record ConsentStatus(
	List<ConsentDocumentType> acceptedDocumentTypes,
	List<ConsentDocumentType> requiredDocumentTypes,
	boolean hasAcceptedRequired
) {
}
