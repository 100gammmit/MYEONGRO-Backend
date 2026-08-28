package com.myeongro.api.domain.dailycard.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.myeongro.api.domain.dailycard.config.DailyCardProperties;
import com.myeongro.api.domain.dailycard.controller.DailyCardSelectionResponse;
import com.myeongro.api.domain.dailycard.exception.DailyCardContentVersionMismatchException;
import com.myeongro.api.domain.dailycard.exception.InvalidDailyCardSelectionException;
import com.myeongro.api.domain.tarot.model.MajorArcana;
import com.myeongro.api.domain.tarot.selection.TarotCardRanker;

@Service
public class DailyCardSelectionService {

	private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
	private static final String CARD_NAMESPACE = "daily-card-selection-v1";
	private static final String VARIANT_NAMESPACE = "daily-card-variant-v1";

	private final TarotCardRanker cardRanker;
	private final DailyCardProperties properties;
	private final Clock clock;

	@Autowired
	public DailyCardSelectionService(
		TarotCardRanker cardRanker,
		DailyCardProperties properties
	) {
		this(cardRanker, properties, Clock.systemUTC());
	}

	DailyCardSelectionService(
		TarotCardRanker cardRanker,
		DailyCardProperties properties,
		Clock clock
	) {
		this.cardRanker = cardRanker;
		this.properties = properties;
		this.clock = clock;
	}

	public DailyCardSelectionResponse select(
		UUID drawId,
		int selectedSlot,
		String contentVersion
	) {
		if (drawId == null) {
			throw new InvalidDailyCardSelectionException(
				"INVALID_DRAW_ID", "drawId", "drawId가 필요합니다."
			);
		}
		if (selectedSlot < 1 || selectedSlot > 5) {
			throw new InvalidDailyCardSelectionException(
				"INVALID_SELECTED_SLOT", "selectedSlot", "selectedSlot은 1에서 5 사이여야 합니다."
			);
		}
		if (!properties.contentVersion().equals(contentVersion)) {
			throw new DailyCardContentVersionMismatchException();
		}
		LocalDate dateKst = LocalDate.now(clock.withZone(SERVICE_ZONE));
		List<String> context = List.of(
			dateKst.toString(), drawId.toString(), contentVersion
		);
		List<String> candidates = cardRanker.rank(
			CARD_NAMESPACE, context, MajorArcana.all()
		).subList(0, 5);
		String cardId = candidates.get(selectedSlot - 1);
		List<String> variants = IntStream.range(0, properties.variantCount())
			.mapToObj(Integer::toString)
			.toList();
		int variantIndex = Integer.parseInt(cardRanker.rank(
			VARIANT_NAMESPACE,
			List.of(dateKst.toString(), drawId.toString(), contentVersion, cardId),
			variants
		).getFirst());
		return new DailyCardSelectionResponse(new DailyCardSelectionResponse.Selection(
			dateKst, cardId, variantIndex, contentVersion
		));
	}
}
