package com.myeongro.api.domain.tarotdraw.exception;

import org.springframework.http.HttpStatus;

public class TarotDrawSessionException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	private TarotDrawSessionException(String code, HttpStatus status, String message) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public String code() {
		return code;
	}

	public HttpStatus status() {
		return status;
	}

	public static TarotDrawSessionException invalidSpread() {
		return new TarotDrawSessionException(
			"INVALID_SPREAD_TYPE", HttpStatus.BAD_REQUEST, "Invalid tarot spread type."
		);
	}

	public static TarotDrawSessionException invalidSelection() {
		return new TarotDrawSessionException(
			"INVALID_DRAW_SELECTION", HttpStatus.BAD_REQUEST, "Invalid draw selection."
		);
	}

	public static TarotDrawSessionException notFound() {
		return new TarotDrawSessionException(
			"DRAW_SESSION_NOT_FOUND", HttpStatus.NOT_FOUND, "Tarot draw session was not found."
		);
	}

	public static TarotDrawSessionException active() {
		return new TarotDrawSessionException(
			"DRAW_SESSION_ACTIVE", HttpStatus.CONFLICT, "An active tarot draw session already exists."
		);
	}

	public static TarotDrawSessionException stateConflict() {
		return new TarotDrawSessionException(
			"DRAW_SESSION_STATE_CONFLICT", HttpStatus.CONFLICT, "Tarot draw session state has changed."
		);
	}

	public static TarotDrawSessionException alreadyConsumed() {
		return new TarotDrawSessionException(
			"DRAW_SESSION_ALREADY_CONSUMED", HttpStatus.CONFLICT, "Tarot draw session was already consumed."
		);
	}
}
