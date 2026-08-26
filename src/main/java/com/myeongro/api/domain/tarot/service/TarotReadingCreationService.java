package com.myeongro.api.domain.tarot.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.myeongro.api.domain.reading.dto.CreatedReadingResponse;
import com.myeongro.api.domain.reading.service.ReadingCreationWorkflow;
import com.myeongro.api.domain.tarot.controller.TarotReadingCreateRequest;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;

@Service
public class TarotReadingCreationService {

	private final ReadingCreationWorkflow workflow;
	private final TarotCardSelector cardSelector;
	private final TarotReadingInputNormalizer inputNormalizer;

	public TarotReadingCreationService(
		ReadingCreationWorkflow workflow,
		TarotCardSelector cardSelector,
		TarotReadingInputNormalizer inputNormalizer
	) {
		this.workflow = workflow;
		this.cardSelector = cardSelector;
		this.inputNormalizer = inputNormalizer;
	}

	public CreatedReadingResponse create(
		UUID userId,
		UUID requestId,
		TarotReadingCreateRequest request
	) {
		return workflow.create(
			userId,
			requestId,
			() -> {
				TarotSpreadType spread = TarotSpreadType.fromValue(request.spreadType());
				var cardIds = cardSelector.select(
					userId, requestId, spread, request.selectedSlots()
				);
				return inputNormalizer.normalize(request, spread, cardIds);
			},
			input -> input
		);
	}
}
