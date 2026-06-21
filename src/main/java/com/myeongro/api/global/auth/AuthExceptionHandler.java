package com.myeongro.api.global.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.myeongro.api.global.auth.exception.InvalidAuthenticatedUserException;
import com.myeongro.api.global.auth.exception.UnauthenticatedUserException;

@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler({
		UnauthenticatedUserException.class,
		InvalidAuthenticatedUserException.class
	})
	public ResponseEntity<Map<String, String>> unauthorized(RuntimeException exception) {
		return ResponseEntity.status(401).body(Map.of("error", exception.getMessage()));
	}
}
