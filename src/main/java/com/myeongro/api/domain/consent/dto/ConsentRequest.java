package com.myeongro.api.domain.consent.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;

public class ConsentRequest {

	private List<ConsentDocumentType> acceptedDocumentTypes;

	public List<ConsentDocumentType> getAcceptedDocumentTypes() {
		return acceptedDocumentTypes;
	}

	public void setAcceptedDocumentTypes(
		List<ConsentDocumentType> acceptedDocumentTypes
	) {
		this.acceptedDocumentTypes = acceptedDocumentTypes;
	}

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw new IllegalArgumentException("Unknown field: " + name);
	}
}
