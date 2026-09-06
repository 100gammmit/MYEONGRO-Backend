package com.myeongro.api.domain.consent.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.consent.dto.ConsentAcceptanceRequest;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.consent.service.ConsentVersionMismatchException;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/consents")
@RequiredArgsConstructor
public class ConsentController {

	private final ConsentService consentService;
	private final AuthenticatedUserResolver userResolver;

	@GetMapping
	public ResponseEntity<Map<String, ConsentStatus>> getStatus(
		@RequestParam String scope,
		Authentication authentication
	) {
		return ResponseEntity.ok(Map.of(
			"status",
			consentService.getUserStatus(
				userResolver.requireUser(authentication).id(),
				ConsentScope.fromValue(scope)
			)
		));
	}

	@PostMapping("/{documentType}")
	public ResponseEntity<Map<String, Object>> accept(
		@PathVariable String documentType,
		@Valid @RequestBody ConsentAcceptanceRequest request,
		Authentication authentication
	) {
		return ResponseEntity.ok(Map.of(
			"consent",
			consentService.acceptForUser(
				userResolver.requireUser(authentication).id(),
				ConsentDocumentType.fromValue(documentType),
				request.getDocumentVersion()
			)
		));
	}

	@DeleteMapping("/{documentType}")
	public ResponseEntity<Void> withdraw(
		@PathVariable String documentType,
		Authentication authentication
	) {
		consentService.withdrawForUser(
			userResolver.requireUser(authentication).id(),
			ConsentDocumentType.fromValue(documentType)
		);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(ConsentVersionMismatchException.class)
	public ResponseEntity<Map<String, String>> versionMismatch(
		ConsentVersionMismatchException exception
	) {
		return ResponseEntity.status(409).body(Map.of(
			"code", "CONSENT_VERSION_MISMATCH",
			"message", exception.getMessage()
		));
	}

	@ExceptionHandler({
		IllegalArgumentException.class,
		HttpMessageNotReadableException.class,
		MethodArgumentNotValidException.class
	})
	public ResponseEntity<Map<String, String>> badRequest(Exception exception) {
		String message = exception instanceof IllegalArgumentException
			? exception.getMessage()
			: "Invalid consent request";
		return ResponseEntity.badRequest().body(Map.of("error", message));
	}
}
