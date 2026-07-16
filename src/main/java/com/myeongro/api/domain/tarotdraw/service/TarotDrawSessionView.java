package com.myeongro.api.domain.tarotdraw.service;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TarotDrawSessionView(
	String drawSessionId,
	String spreadType,
	String status,
	String currentPosition,
	int selectedCount,
	int totalCount,
	Instant expiresAt,
	List<TarotDrawCandidateView> candidates,
	List<TarotDrawCardView> cards
) {
}
