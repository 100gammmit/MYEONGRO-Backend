package com.myeongro.api.domain.tarot.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.tarot.service.TarotReadingCreationService;
import com.myeongro.api.domain.reading.controller.ReadingApiController;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tarot/readings")
@ReadingApiController
@Tag(name = "Tarot readings", description = "타로 리딩 생성 API")
public class TarotReadingController {

	private final TarotReadingCreationService service;
	private final AuthenticatedUserResolver userResolver;

	public TarotReadingController(
		TarotReadingCreationService service,
		AuthenticatedUserResolver userResolver
	) {
		this.service = service;
		this.userResolver = userResolver;
	}

	@PostMapping
	@Operation(summary = "타로 리딩 생성", description = "로그인 사용자의 동의 확인 후 타로 리딩을 생성합니다.")
	public ResponseEntity<Map<String, Object>> createReading(
		@Valid @RequestBody TarotReadingCreateRequest request,
		Authentication authentication
	) {
		UUID userId = userResolver.requireUser(authentication).id();
		return ResponseEntity.ok(Map.of(
			"reading", service.create(userId, request.requestId(), request)
		));
	}
}
