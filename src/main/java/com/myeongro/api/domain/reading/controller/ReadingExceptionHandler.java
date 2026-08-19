package com.myeongro.api.domain.reading.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.exception.ReadingIdempotencyConflictException;
import com.myeongro.api.domain.reading.exception.ReadingRecordNotFoundException;
import com.myeongro.api.domain.reading.exception.ReadingRetryNotAllowedException;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
@RestControllerAdvice(annotations = ReadingApiController.class)
public class ReadingExceptionHandler {

	@ExceptionHandler(RequiredConsentMissingException.class)
	public ResponseEntity<Map<String, String>> forbidden(Exception exception) {
		return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
	}

	@ExceptionHandler(ReadingIdempotencyConflictException.class)
	public ResponseEntity<Map<String, String>> idempotencyConflict(Exception exception) {
		return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
	}

	@ExceptionHandler(ReadingRecordNotFoundException.class)
	public ResponseEntity<Map<String, String>> notFound(Exception exception) {
		return ResponseEntity.status(404).body(Map.of("error", "리딩을 찾을 수 없습니다."));
	}

	@ExceptionHandler(ReadingRetryNotAllowedException.class)
	public ResponseEntity<Map<String, String>> retryConflict(Exception exception) {
		return ResponseEntity.status(409).body(Map.of("error", "재시도할 수 없는 리딩입니다."));
	}

	@ExceptionHandler(OpenAiReadingGenerationException.class)
	public ResponseEntity<Map<String, Object>> generationFailed(
		OpenAiReadingGenerationException exception
	) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("error", exception.getMessage());
		body.put("code", exception.getCode());
		if (exception.getReadingId() != null) {
			body.put("readingId", exception.getReadingId());
		}
		return ResponseEntity.status(502).body(body);
	}

	@ExceptionHandler(InvalidReadingRequestException.class)
	public ResponseEntity<Map<String, String>> invalidRequest(
		InvalidReadingRequestException exception
	) {
		return invalidRequestBody(exception);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, String>> unreadable(
		HttpMessageNotReadableException exception
	) {
		InvalidReadingRequestException invalid = findInvalidRequest(exception);
		return invalid != null ? invalidRequestBody(invalid) : genericBadRequest();
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, String>> invalidArguments(
		MethodArgumentNotValidException exception
	) {
		String field = exception.getBindingResult().getFieldErrors().isEmpty()
			? "request"
			: exception.getBindingResult().getFieldErrors().getFirst().getField();
		return invalidRequestBody(new InvalidReadingRequestException(
			"INVALID_READING_REQUEST", field, "필수 입력을 확인해 주세요."
		));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, String>> badRequest(Exception exception) {
		return genericBadRequest();
	}

	private ResponseEntity<Map<String, String>> invalidRequestBody(
		InvalidReadingRequestException exception
	) {
		return ResponseEntity.badRequest().body(Map.of(
			"code", exception.getCode(),
			"field", exception.getField(),
			"message", exception.getMessage()
		));
	}

	private ResponseEntity<Map<String, String>> genericBadRequest() {
		return ResponseEntity.badRequest().body(Map.of(
			"code", "INVALID_READING_REQUEST",
			"field", "request",
			"message", "잘못된 리딩 요청입니다."
		));
	}

	private InvalidReadingRequestException findInvalidRequest(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof InvalidReadingRequestException invalid) {
				return invalid;
			}
			current = current.getCause();
		}
		return null;
	}
}
