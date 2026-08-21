package com.myeongro.api.domain.readingcredit.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.readingcredit.dto.ReadingCreditStatusResponse;
import com.myeongro.api.domain.readingcredit.service.ReadingCreditService;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/reading-credits")
@Tag(name = "Reading credits", description = "리딩 크레딧 조회 API")
public class ReadingCreditController {

	private final ReadingCreditService service;
	private final AuthenticatedUserResolver userResolver;

	public ReadingCreditController(
		ReadingCreditService service,
		AuthenticatedUserResolver userResolver
	) {
		this.service = service;
		this.userResolver = userResolver;
	}

	@GetMapping
	@Operation(summary = "내 리딩 크레딧 조회")
	public ResponseEntity<ReadingCreditStatusResponse> getStatus(
		Authentication authentication
	) {
		var user = userResolver.requireUser(authentication);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(service.getStatus(user.id()));
	}
}
