package com.myeongro.api.domain.tarotdraw.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.tarotdraw.service.TarotDrawSessionService;
import com.myeongro.api.domain.tarotdraw.service.TarotDrawSessionView;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/tarot/draw-sessions")
@Tag(name = "Tarot Draw Sessions")
public class TarotDrawSessionController {

	private final TarotDrawSessionService service;
	private final AuthenticatedUserResolver userResolver;

	public TarotDrawSessionController(
		TarotDrawSessionService service,
		AuthenticatedUserResolver userResolver
	) {
		this.service = service;
		this.userResolver = userResolver;
	}

	@GetMapping("/active")
	@Operation(summary = "Get the authenticated user's active tarot draw session")
	public TarotDrawSessionView getActive(Authentication authentication) {
		return service.getActive(userId(authentication));
	}

	@PostMapping
	@Operation(summary = "Create a tarot draw session")
	public TarotDrawSessionView create(
		Authentication authentication,
		@Valid @RequestBody TarotDrawSessionCreateRequest request
	) {
		return service.create(userId(authentication), request.spreadType());
	}

	@PostMapping("/{drawSessionId}/selections")
	@Operation(summary = "Select one opaque candidate for the current position")
	public TarotDrawSessionView select(
		Authentication authentication,
		@PathVariable String drawSessionId,
		@Valid @RequestBody TarotDrawSelectionRequest request
	) {
		return service.select(userId(authentication), drawSessionId, request.candidateToken());
	}

	@DeleteMapping("/{drawSessionId}")
	@Operation(summary = "Abandon an active or unconsumed complete tarot draw session")
	public ResponseEntity<Void> abandon(
		Authentication authentication,
		@PathVariable String drawSessionId
	) {
		service.abandon(userId(authentication), drawSessionId);
		return ResponseEntity.noContent().build();
	}

	private UUID userId(Authentication authentication) {
		return userResolver.requireUser(authentication).id();
	}
}
