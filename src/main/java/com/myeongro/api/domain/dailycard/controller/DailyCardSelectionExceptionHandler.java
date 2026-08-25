package com.myeongro.api.domain.dailycard.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.myeongro.api.domain.dailycard.exception.DailyCardContentVersionMismatchException;
import com.myeongro.api.domain.dailycard.exception.InvalidDailyCardSelectionException;

@RestControllerAdvice(assignableTypes = DailyCardSelectionController.class)
public class DailyCardSelectionExceptionHandler {

	@ExceptionHandler(DailyCardContentVersionMismatchException.class)
	public ResponseEntity<Map<String, String>> versionMismatch(
		DailyCardContentVersionMismatchException exception
	) {
		return ResponseEntity.status(409).body(Map.of(
			"code", "DAILY_CARD_CONTENT_VERSION_MISMATCH",
			"message", exception.getMessage()
		));
	}

	@ExceptionHandler(InvalidDailyCardSelectionException.class)
	public ResponseEntity<Map<String, String>> invalidSelection(
		InvalidDailyCardSelectionException exception
	) {
		return invalidBody(exception);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> unreadable(
		HttpMessageNotReadableException exception
	) {
		InvalidDailyCardSelectionException invalid = findInvalid(exception);
		return invalid != null ? invalidBody(invalid) : genericBadRequest();
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> invalidArguments(
		MethodArgumentNotValidException exception
	) {
		String field = exception.getBindingResult().getFieldErrors().isEmpty()
			? "request"
			: exception.getBindingResult().getFieldErrors().getFirst().getField();
		return invalidBody(new InvalidDailyCardSelectionException(
			"INVALID_DAILY_CARD_SELECTION", field, "카드 선택 요청을 확인해 주세요."
		));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badRequest() {
		return genericBadRequest();
	}

	private ResponseEntity<Map<String, String>> invalidBody(
		InvalidDailyCardSelectionException exception
	) {
		return ResponseEntity.badRequest().body(Map.of(
			"code", exception.getCode(),
			"field", exception.getField(),
			"message", exception.getMessage()
		));
	}

	private ResponseEntity<Map<String, String>> genericBadRequest() {
		return invalidBody(new InvalidDailyCardSelectionException(
			"INVALID_DAILY_CARD_SELECTION", "request", "잘못된 카드 선택 요청입니다."
		));
	}

	private InvalidDailyCardSelectionException findInvalid(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof InvalidDailyCardSelectionException invalid) {
				return invalid;
			}
			current = current.getCause();
		}
		return null;
	}
}
