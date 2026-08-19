package com.myeongro.api.domain.saju.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.saju.service.SajuReadingCreationService;
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
	private final AuthenticatedUserResolver userResolver;

	public SajuReadingController(
		SajuReadingCreationService service,
		AuthenticatedUserResolver userResolver
	) {
		this.service = service;
		this.userResolver = userResolver;
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
