package com.myeongro.api.domain.saju.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.myeongro.api.domain.saju.calculation.SajuCalculationException;

@RestControllerAdvice(assignableTypes = SajuReadingController.class)
public class SajuReadingExceptionHandler {

	@ExceptionHandler(SajuCalculationException.class)
	public ResponseEntity<Map<String, String>> calculationFailed(
		SajuCalculationException exception
	) {
		return ResponseEntity.internalServerError().body(Map.of(
			"error", exception.getMessage(),
			"code", exception.getCode()
		));
	}
}
