package com.myeongro.api.domain.reading.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.reading.exception.FreeReadingIdempotencyConflictException;
import com.myeongro.api.domain.reading.exception.FreeReadingQuotaExceededException;
import com.myeongro.api.domain.reading.exception.ReadingRetryNotAllowedException;
import com.myeongro.api.domain.reading.exception.OpenAiReadingGenerationException;
import com.myeongro.api.domain.reading.service.ReadingCreationService;
import com.myeongro.api.domain.reading.exception.ReadingRecordNotFoundException;
import com.myeongro.api.domain.reading.service.ReadingRecordsService;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;
import com.myeongro.api.global.auth.session.SessionPrincipal;
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
	private final ReadingRecordsService recordsService;
	private final GuestSessionSigner signer;
	private final AuthenticatedUserResolver userResolver;

	public ReadingController(
		ReadingCreationService service,
		ReadingRecordsService recordsService,
		GuestSessionSigner signer,
		AuthenticatedUserResolver userResolver
	) {
		this.service = service;
		this.recordsService = recordsService;
		this.signer = signer;
		this.userResolver = userResolver;
	}

	@PostMapping
	@Operation(summary = "무료 게스트 리딩 생성", description = "동의 확인 후 쿼터를 예약하고 generating 상태의 리딩을 생성합니다.")
	public ResponseEntity<Map<String, Object>> createReading(
		@CookieValue(
			name = CookieService.GUEST_COOKIE_NAME,
			required = false
		) String token,
		@Valid @RequestBody ReadingCreateRequest request,
		Authentication authentication,
		HttpServletRequest servletRequest
	) {
		if (isAuthenticatedUser(authentication)) {
			UUID userId = userResolver.requireUser(authentication).id();
			return ResponseEntity.ok(Map.of(
				"reading",
				service.createUserReading(
					userId,
					servletRequest.getRemoteAddr(),
					request.requestId(),
					request
				)
			));
		}

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

	@GetMapping
	@Operation(summary = "내 리딩 기록 목록 조회")
	public ResponseEntity<Map<String, Object>> listReadings(Authentication authentication) {
		UUID userId = userResolver.requireUser(authentication).id();
		return ResponseEntity.ok(Map.of("items", recordsService.listByUser(userId)));
	}

	private boolean isAuthenticatedUser(Authentication authentication) {
		return authentication != null
			&& authentication.isAuthenticated()
			&& authentication.getPrincipal() instanceof SessionPrincipal;
	}

	@GetMapping("/{readingId}")
	@Operation(summary = "내 리딩 기록 상세 조회")
	public ResponseEntity<Map<String, Object>> getReading(
		Authentication authentication,
		@PathVariable UUID readingId
	) {
		UUID userId = userResolver.requireUser(authentication).id();
		return ResponseEntity.ok(Map.of(
			"reading",
			recordsService.getByUserAndId(userId, readingId)
		));
	}

	@DeleteMapping("/{readingId}")
	@Operation(summary = "내 리딩 기록 삭제")
	public ResponseEntity<Void> deleteReading(
		Authentication authentication,
		@PathVariable UUID readingId
	) {
		UUID userId = userResolver.requireUser(authentication).id();
		recordsService.deleteByUserAndId(userId, readingId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{readingId}/retry")
	@Operation(summary = "실패한 내 리딩 재시도")
	public ResponseEntity<Map<String, Object>> retryReading(
		Authentication authentication,
		@PathVariable UUID readingId
	) {
		UUID userId = userResolver.requireUser(authentication).id();
		return ResponseEntity.ok(Map.of(
			"reading",
			recordsService.retry(userId, readingId)
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

	@ExceptionHandler(ReadingRecordNotFoundException.class)
	public ResponseEntity<Map<String, String>> notFound(Exception exception) {
		return ResponseEntity.status(404).body(Map.of("error", "리딩을 찾을 수 없습니다."));
	}

	@ExceptionHandler(ReadingRetryNotAllowedException.class)
	public ResponseEntity<Map<String, String>> retryConflict(Exception exception) {
		return ResponseEntity.status(409).body(Map.of("error", "재시도할 수 없는 리딩입니다."));
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
