package com.myeongro.api.domain.saju.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.saju.service.SajuAnnualDetailLevelService;
import com.myeongro.api.domain.saju.service.SajuReadingCreationService;
import com.myeongro.api.global.ratelimit.IpRateLimiter;
import com.myeongro.api.domain.reading.controller.ReadingApiController;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/saju/readings")
@ReadingApiController
@Tag(name = "Saju readings", description = "사주 리딩 생성 API")
public class SajuReadingController {

	private final SajuReadingCreationService service;
	private final SajuAnnualDetailLevelService detailLevelService;
	private final IpRateLimiter rateLimiter;
	private final AuthenticatedUserResolver userResolver;

	public SajuReadingController(
		SajuReadingCreationService service,
		SajuAnnualDetailLevelService detailLevelService,
		IpRateLimiter rateLimiter,
		AuthenticatedUserResolver userResolver
	) {
		this.service = service;
		this.detailLevelService = detailLevelService;
		this.rateLimiter = rateLimiter;
		this.userResolver = userResolver;
	}

	@PostMapping("/detail-level")
	@Operation(summary = "한 해 리포트 형식 확인",
		description = "출생정보로 상세형·간략형과 차감될 크레딧을 알려 줍니다. 계산만 하며 저장·차감·AI 호출을 하지 않습니다.")
	public ResponseEntity<Map<String, Object>> annualDetailLevel(
		@Valid @RequestBody SajuDetailLevelRequest request,
		Authentication authentication
	) {
		UUID userId = userResolver.requireUser(authentication).id();
		// Counted per user: the Front proxies this call, so every visitor arrives from the web server's address.
		// An unavailable counter does not block a signed-in, consented check; only an exceeded one does.
		if (rateLimiter.acquireForUser(IpRateLimiter.Scope.SAJU_DETAIL_LEVEL, userId) == IpRateLimiter.Decision.LIMITED) {
			return ResponseEntity.status(429).header("Retry-After", "60").cacheControl(CacheControl.noStore()).body(Map.of(
				"code", "SAJU_DETAIL_LEVEL_RATE_LIMITED",
				"message", "확인 요청이 많아요. 잠시 후 다시 시도해 주세요."
			));
		}
		var result = detailLevelService.check(userId, request);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
			"detailLevel", result.detailLevel(),
			"creditCost", result.creditCost()
		));
	}

	@ExceptionHandler(SajuAnnualDetailLevelService.Busy.class)
	ResponseEntity<Map<String, Object>> detailLevelBusy() {
		return ResponseEntity.status(503).header("Retry-After", "5").cacheControl(CacheControl.noStore()).body(Map.of(
			"code", "SAJU_DETAIL_LEVEL_BUSY",
			"message", "지금은 형식을 확인하지 못했어요. 잠시 후 다시 시도해 주세요."
		));
	}

	@PostMapping
	@Operation(summary = "사주 리딩 생성", description = "로그인 사용자의 동의 확인 후 사주 리딩을 생성합니다.")
	public ResponseEntity<Map<String, Object>> createReading(
		@Valid @RequestBody SajuReadingCreateRequest request,
		Authentication authentication
	) {
		UUID userId = userResolver.requireUser(authentication).id();
		return ResponseEntity.ok(Map.of(
			"reading", service.create(userId, request.requestId(), request)
		));
	}
}
