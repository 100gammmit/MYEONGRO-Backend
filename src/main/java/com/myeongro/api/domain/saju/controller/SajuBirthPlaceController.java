package com.myeongro.api.domain.saju.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.consent.service.ConsentService;
import com.myeongro.api.domain.reading.exception.RequiredConsentMissingException;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/saju/birth-places")
@Tag(name = "Saju", description = "사주 리딩 보조 API")
public class SajuBirthPlaceController {

	private final SajuBirthPlaceCatalog catalog;
	private final ConsentService consentService;
	private final AuthenticatedUserResolver userResolver;

	public SajuBirthPlaceController(
		SajuBirthPlaceCatalog catalog,
		ConsentService consentService,
		AuthenticatedUserResolver userResolver
	) {
		this.catalog = catalog;
		this.consentService = consentService;
		this.userResolver = userResolver;
	}

	@GetMapping
	@Operation(summary = "국내 출생지 목록 조회")
	public ResponseEntity<Map<String, Object>> list(Authentication authentication) {
		UUID userId = userResolver.requireUser(authentication).id();
		if (!consentService.getUserStatus(userId).hasAcceptedRequired()) {
			throw new RequiredConsentMissingException("필수 동의가 필요합니다.");
		}
		return ResponseEntity.ok(Map.of(
			"version", catalog.version(),
			"provinces", catalog.provinces()
		));
	}

	@ExceptionHandler(RequiredConsentMissingException.class)
	public ResponseEntity<Map<String, String>> forbidden(Exception exception) {
		return ResponseEntity.status(403).body(Map.of("error", exception.getMessage()));
	}
}
