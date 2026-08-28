package com.myeongro.api.domain.dailycard.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.dailycard.service.DailyCardSelectionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tarot/daily-card-selections")
@Tag(name = "Daily tarot card", description = "무료 오늘의 한 장 선택 API")
public class DailyCardSelectionController {

	private final DailyCardSelectionService service;

	public DailyCardSelectionController(DailyCardSelectionService service) {
		this.service = service;
	}

	@PostMapping
	@Operation(summary = "오늘의 한 장 선택", description = "게스트와 로그인 사용자에게 동일한 무료 카드 선택 결과를 반환합니다.")
	public ResponseEntity<DailyCardSelectionResponse> select(
		@Valid @RequestBody DailyCardSelectionRequest request
	) {
		return ResponseEntity.ok(service.select(
			request.drawId(), request.selectedSlot(), request.contentVersion()
		));
	}
}
