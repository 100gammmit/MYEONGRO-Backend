package com.myeongro.api.domain.reading.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.entity.TarotSpreadType;

@Component
public class TarotPromptCatalog {

	private final PromptPart common;
	private final PromptPart cards;
	private final Map<TarotSpreadType, PromptPart> spreads;

	public TarotPromptCatalog(
		@Value("${app.reading.prompts.tarot.common}") Resource common,
		@Value("${app.reading.prompts.tarot.cards}") Resource cards,
		@Value("${app.reading.prompts.tarot.spreads.daily-one-card}") Resource daily,
		@Value("${app.reading.prompts.tarot.spreads.mind-three-card}") Resource mind,
		@Value("${app.reading.prompts.tarot.spreads.relationship-three-card}") Resource relationship,
		@Value("${app.reading.prompts.tarot.spreads.choice-five-card}") Resource choice
	) {
		this.common = read(common);
		this.cards = read(cards);
		EnumMap<TarotSpreadType, PromptPart> configured =
			new EnumMap<>(TarotSpreadType.class);
		configured.put(TarotSpreadType.DAILY_ONE_CARD, read(daily));
		configured.put(TarotSpreadType.MIND_THREE_CARD, read(mind));
		configured.put(TarotSpreadType.RELATIONSHIP_THREE_CARD, read(relationship));
		configured.put(TarotSpreadType.CHOICE_FIVE_CARD, read(choice));
		this.spreads = Map.copyOf(configured);
	}

	public String prompt(TarotSpreadType spreadType) {
		return String.join("\n\n", common.content(), cards.content(), part(spreadType).content());
	}

	public String version(TarotSpreadType spreadType) {
		return String.join("+", common.version(), cards.version(), part(spreadType).version());
	}

	private PromptPart part(TarotSpreadType spreadType) {
		if (spreadType == null || !spreads.containsKey(spreadType)) {
			throw new IllegalArgumentException("Tarot spread type is required");
		}
		return spreads.get(spreadType);
	}

	private PromptPart read(Resource resource) {
		try {
			String content = resource.getContentAsString(StandardCharsets.UTF_8);
			if (content.isBlank()) {
				throw new IllegalArgumentException("Tarot prompt is empty");
			}
			String filename = resource.getFilename();
			if (filename == null || !filename.endsWith(".md")) {
				throw new IllegalArgumentException("Versioned tarot prompt filename is required");
			}
			return new PromptPart(filename.substring(0, filename.length() - 3), content);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot read tarot prompt", exception);
		}
	}

	private record PromptPart(String version, String content) {
	}
}
