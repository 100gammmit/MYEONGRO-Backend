package com.myeongro.api.domain.consent.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public class ConsentCompletionRequest {

	@NotBlank
	private String scope;

	@Valid
	@NotEmpty
	@Size(max = 3)
	private Map<@NotBlank String, @NotBlank String> documentVersions;

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public Map<String, String> getDocumentVersions() {
		return documentVersions;
	}

	public void setDocumentVersions(Map<String, String> documentVersions) {
		this.documentVersions = documentVersions;
	}

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw new IllegalArgumentException("Unknown field: " + name);
	}
}
