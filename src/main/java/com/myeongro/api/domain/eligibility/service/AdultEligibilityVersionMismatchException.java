package com.myeongro.api.domain.eligibility.service;

public class AdultEligibilityVersionMismatchException extends RuntimeException {

	public AdultEligibilityVersionMismatchException() {
		super("Adult eligibility confirmation is missing or outdated");
	}
}
