package com.myeongro.api.domain.consent.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.consent.dto.ConsentRequest;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/consents")
@RequiredArgsConstructor
public class ConsentController {

	private final ConsentService consentService;
	private final AuthenticatedUserResolver userResolver;

	@GetMapping
	public ResponseEntity<Map<String, ConsentStatus>> getStatus(
		Authentication authentication
	) {
		return ResponseEntity.ok(Map.of(
			"status",
			consentService.getUserStatus(userResolver.requireUser(authentication).id())
		));
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> acceptRequired(
		@RequestBody ConsentRequest request,
		Authentication authentication
	) {
		return ResponseEntity.ok(Map.of(
			"consents",
			consentService.acceptRequiredForUser(
				userResolver.requireUser(authentication).id(),
				request.getAcceptedDocumentTypes()
			)
		));
	}

	@ExceptionHandler({
		IllegalArgumentException.class,
		HttpMessageNotReadableException.class
	})
	public ResponseEntity<Map<String, String>> badRequest(Exception exception) {
		String message = exception instanceof IllegalArgumentException
			? exception.getMessage()
			: "Invalid consent request";
		return ResponseEntity.badRequest().body(Map.of("error", message));
	}
}
