package com.myeongro.api.domain.tarotdraw.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.myeongro.api.domain.tarotdraw.exception.TarotDrawSessionException;
import com.myeongro.api.domain.reading.controller.ReadingController;

@RestControllerAdvice(assignableTypes = {
	TarotDrawSessionController.class,
	ReadingController.class
})
public class TarotDrawSessionExceptionHandler {

	@ExceptionHandler(TarotDrawSessionException.class)
	public ResponseEntity<Map<String, String>> handle(TarotDrawSessionException exception) {
		return ResponseEntity.status(exception.status()).body(Map.of(
			"code", exception.code(),
			"message", exception.getMessage()
		));
	}

	@ExceptionHandler({
		IllegalArgumentException.class,
		HttpMessageNotReadableException.class,
		MethodArgumentNotValidException.class
	})
	public ResponseEntity<Map<String, String>> invalidSelection(Exception exception) {
		return ResponseEntity.badRequest().body(Map.of(
			"code", "INVALID_DRAW_SELECTION",
			"message", "Invalid draw selection."
		));
	}
}
