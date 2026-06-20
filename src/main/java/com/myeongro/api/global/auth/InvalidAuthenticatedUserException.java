package com.myeongro.api.global.auth;

public class InvalidAuthenticatedUserException extends RuntimeException {

	public InvalidAuthenticatedUserException(Throwable cause) {
		super("Authenticated user subject is not a valid UUID", cause);
	}
}
