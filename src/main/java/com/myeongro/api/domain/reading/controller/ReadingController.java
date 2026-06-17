package com.myeongro.api.domain.reading.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.reading.repository.FreeReadingIdempotencyConflictException;
import com.myeongro.api.domain.reading.repository.FreeReadingQuotaExceededException;
import com.myeongro.api.domain.reading.service.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.service.ReadingCreationService;
import com.myeongro.api.domain.reading.service.RequiredConsentMissingException;
import com.myeongro.api.global.cookie.CookieService;
import com.myeongro.api.global.guest.GuestSession;
import com.myeongro.api.global.guest.GuestSessionSigner;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/readings")
@Tag(name = "Readings", description = "타로·사주 리딩 생성 API")
public class ReadingController {

	private final ReadingCreationService service;
	private final GuestSessionSigner signer;

	public ReadingController(
		ReadingCreationService service,
		GuestSessionSigner signer
	) {
		this.service = service;
		this.signer = signer;
	}

	@PostMapping
	@Operation(summary = "무료 게스트 리딩 생성", description = "동의 확인 후 쿼터를 예약하고 generating 상태의 리딩을 생성합니다.")
	public ResponseEntity<Map<String, Object>> createReading(
		@CookieValue(
			name = CookieService.GUEST_COOKIE_NAME,
			required = false
		) String token,
		@Valid @RequestBody ReadingCreateRequest request,
		HttpServletRequest servletRequest
	) {
		GuestSession session = signer.verify(token)
			.orElseThrow(() -> new GuestSessionRequiredException(
				"로그인 또는 게스트 세션이 필요합니다."
			));
		UUID requestId = request.requestId();
		return ResponseEntity.ok(Map.of(
			"reading",
			service.createGuestReading(
				session.sessionId(),
				servletRequest.getRemoteAddr(),
				requestId,
				request
			)
		));
	}

	@ExceptionHandler(GuestSessionRequiredException.class)
	public ResponseEntity<Map<String, String>> unauthorized(Exception exception) {
		return ResponseEntity.status(401).body(Map.of("error", exception.getMessage()));
	}

	@ExceptionHandler(RequiredConsentMissingException.class)
	public ResponseEntity<Map<String, String>> forbidden(Exception exception) {
		return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
	}

	@ExceptionHandler(FreeReadingQuotaExceededException.class)
	public ResponseEntity<Map<String, String>> quotaExceeded(Exception exception) {
		return ResponseEntity.status(429).body(Map.of("error", exception.getMessage()));
	}

	@ExceptionHandler(FreeReadingIdempotencyConflictException.class)
	public ResponseEntity<Map<String, String>> idempotencyConflict(Exception exception) {
		return ResponseEntity.status(409).body(Map.of("error", exception.getMessage()));
	}

	@ExceptionHandler(OpenAiReadingGenerationException.class)
	public ResponseEntity<Map<String, String>> generationFailed(
		OpenAiReadingGenerationException exception
	) {
		return ResponseEntity.status(502).body(Map.of(
			"error",
			exception.getMessage(),
			"code",
			exception.getCode()
		));
	}

	@ExceptionHandler({
		IllegalArgumentException.class,
		HttpMessageNotReadableException.class,
		MethodArgumentNotValidException.class
	})
	public ResponseEntity<Map<String, String>> badRequest(Exception exception) {
		return ResponseEntity.badRequest().body(Map.of("error", "잘못된 리딩 요청입니다."));
	}

	private static class GuestSessionRequiredException extends RuntimeException {

		GuestSessionRequiredException(String message) {
			super(message);
		}
	}
}
