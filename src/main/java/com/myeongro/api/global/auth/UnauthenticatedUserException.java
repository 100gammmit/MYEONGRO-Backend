package com.myeongro.api.global.auth;

public class UnauthenticatedUserException extends RuntimeException {

	public UnauthenticatedUserException() {
		super("Authenticated user is required");
	}
}
