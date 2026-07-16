package com.myeongro.api.domain.tarotdraw.service;

import java.util.List;

import com.myeongro.api.domain.reading.entity.TarotSpreadType;

public record CompletedTarotDraw(TarotSpreadType spreadType, List<String> cardIds) {

	public CompletedTarotDraw {
		cardIds = List.copyOf(cardIds);
	}
}
