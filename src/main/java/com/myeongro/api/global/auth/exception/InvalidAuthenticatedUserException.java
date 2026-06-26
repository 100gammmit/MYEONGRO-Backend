package com.myeongro.api.global.auth.exception;

public class InvalidAuthenticatedUserException extends RuntimeException {

	public InvalidAuthenticatedUserException() {
		super("Authenticated user principal is invalid");
	}

	public InvalidAuthenticatedUserException(Throwable cause) {
		super("Authenticated user principal is invalid", cause);
	}
}
