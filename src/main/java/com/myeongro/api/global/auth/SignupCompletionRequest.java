package com.myeongro.api.global.auth;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public class SignupCompletionRequest {

	private Boolean adultEligibilityConfirmed;
	private Boolean termsAccepted;
	private String termsVersion;

	public Boolean getAdultEligibilityConfirmed() {
		return adultEligibilityConfirmed;
	}

	public void setAdultEligibilityConfirmed(Boolean adultEligibilityConfirmed) {
		this.adultEligibilityConfirmed = adultEligibilityConfirmed;
	}

	public Boolean getTermsAccepted() {
		return termsAccepted;
	}

	public void setTermsAccepted(Boolean termsAccepted) {
		this.termsAccepted = termsAccepted;
	}

	public String getTermsVersion() {
		return termsVersion;
	}

	public void setTermsVersion(String termsVersion) {
		this.termsVersion = termsVersion;
	}

	public boolean hasRequiredConfirmations() {
		return Boolean.TRUE.equals(adultEligibilityConfirmed)
			&& Boolean.TRUE.equals(termsAccepted)
			&& termsVersion != null
			&& !termsVersion.isBlank();
	}

	@JsonAnySetter
	void rejectUnknownField(String name, Object value) {
		throw new IllegalArgumentException("Unknown field: " + name);
	}
}
