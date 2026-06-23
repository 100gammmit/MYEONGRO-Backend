package com.myeongro.api.domain.consent.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
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
import com.myeongro.api.global.cookie.CookieService;
import com.myeongro.api.global.guest.GuestService;
import com.myeongro.api.global.guest.GuestSession;
import com.myeongro.api.global.guest.GuestSessionSigner;
import com.myeongro.api.global.guest.IssuedGuestSession;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/consents")
@RequiredArgsConstructor
public class ConsentController {

	private final ConsentService consentService;
	private final GuestService guestService;
	private final GuestSessionSigner signer;
	private final AuthenticatedUserResolver userResolver;

	@GetMapping
	public ResponseEntity<Map<String, ConsentStatus>> getStatus(
		@CookieValue(
			name = CookieService.GUEST_COOKIE_NAME,
			required = false
		) String token,
		Authentication authentication
	) {
		if (isAuthenticatedUser(authentication)) {
			return ResponseEntity.ok(Map.of(
				"status",
				consentService.getUserStatus(userResolver.requireUser(authentication).id())
			));
		}

		Optional<GuestSession> existing = signer.verify(token);
		if (existing.isPresent()) {
			return ResponseEntity.ok(Map.of(
				"status",
				consentService.getStatus(existing.get().sessionId())
			));
		}

		IssuedGuestSession issued = guestService.issueGuest();
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, issued.cookie().toString())
			.body(Map.of(
				"status",
				consentService.getStatus(issued.session().sessionId())
			));
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> acceptRequired(
		@CookieValue(
			name = CookieService.GUEST_COOKIE_NAME,
			required = false
		) String token,
		@RequestBody ConsentRequest request,
		Authentication authentication
	) {
		if (isAuthenticatedUser(authentication)) {
			return ResponseEntity.ok(Map.of(
				"consents",
				consentService.acceptRequiredForUser(
					userResolver.requireUser(authentication).id(),
					request.getAcceptedDocumentTypes()
				)
			));
		}

		GuestSession session = signer.verify(token)
			.orElseThrow(() -> new IllegalArgumentException(
				"Verified guest session is required"
			));
		return ResponseEntity.ok(Map.of(
			"consents",
			consentService.acceptRequired(
				session.sessionId(),
				request.getAcceptedDocumentTypes()
			)
		));
	}

	private boolean isAuthenticatedUser(Authentication authentication) {
		return authentication != null
			&& authentication.isAuthenticated()
			&& authentication.getPrincipal() instanceof Jwt;
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
