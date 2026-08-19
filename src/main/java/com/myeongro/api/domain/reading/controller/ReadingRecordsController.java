package com.myeongro.api.domain.reading.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.reading.service.ReadingRecordsService;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/readings")
@ReadingApiController
@Tag(name = "Reading records", description = "타로·사주 통합 리딩 기록 API")
public class ReadingRecordsController {

	private final ReadingRecordsService recordsService;
	private final AuthenticatedUserResolver userResolver;

	public ReadingRecordsController(
		ReadingRecordsService recordsService,
		AuthenticatedUserResolver userResolver
	) {
		this.recordsService = recordsService;
		this.userResolver = userResolver;
	}

	@GetMapping
	@Operation(summary = "내 리딩 기록 목록 조회")
	public ResponseEntity<Map<String, Object>> listReadings(Authentication authentication) {
		UUID userId = userResolver.requireUser(authentication).id();
		return ResponseEntity.ok(Map.of("items", recordsService.listByUser(userId)));
	}

	@GetMapping("/{readingId}")
	@Operation(summary = "내 리딩 기록 상세 조회")
	public ResponseEntity<Map<String, Object>> getReading(
		Authentication authentication,
		@PathVariable UUID readingId
	) {
		UUID userId = userResolver.requireUser(authentication).id();
		return ResponseEntity.ok(Map.of(
			"reading", recordsService.getByUserAndId(userId, readingId)
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
			"reading", recordsService.retry(userId, readingId)
		));
	}
}
