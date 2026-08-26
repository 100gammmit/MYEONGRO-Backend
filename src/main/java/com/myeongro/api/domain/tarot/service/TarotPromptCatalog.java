package com.myeongro.api.domain.tarot.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.myeongro.api.domain.tarot.model.MajorArcana;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;

@Component
public class TarotPromptCatalog {

	private final PromptPart common;
	private final PromptPart cards;
	private final CardPromptParts cardPromptParts;
	private final Map<TarotSpreadType, PromptPart> spreads;

	public TarotPromptCatalog(
		@Value("${app.reading.prompts.tarot.common}") Resource common,
		@Value("${app.reading.prompts.tarot.cards}") Resource cards,
		@Value("${app.reading.prompts.tarot.spreads.mind-three-card}") Resource mind,
		@Value("${app.reading.prompts.tarot.spreads.relationship-three-card}") Resource relationship,
		@Value("${app.reading.prompts.tarot.spreads.choice-five-card}") Resource choice
	) {
		this.common = read(common);
		this.cards = read(cards);
		this.cardPromptParts = parseCards(this.cards.content());
		EnumMap<TarotSpreadType, PromptPart> configured =
			new EnumMap<>(TarotSpreadType.class);
		configured.put(TarotSpreadType.MIND_THREE_CARD, read(mind));
		configured.put(TarotSpreadType.RELATIONSHIP_THREE_CARD, read(relationship));
		configured.put(TarotSpreadType.CHOICE_FIVE_CARD, read(choice));
		this.spreads = Map.copyOf(configured);
	}

	public String prompt(TarotSpreadType spreadType, List<String> cardIds) {
		return String.join(
			"\n\n",
			common.content(),
			cardPromptParts.select(cardIds),
			part(spreadType).content()
		);
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

	private CardPromptParts parseCards(String content) {
		List<String> lines = content.lines().toList();
		Map<String, String> meanings = new LinkedHashMap<>();
		int firstCard = -1;
		int currentCard = -1;
		int postamble = lines.size();
		for (int index = 0; index < lines.size(); index++) {
			String line = lines.get(index);
			if (line.equals("## 카드 공통 안내")) {
				addCardSection(lines, currentCard, index, meanings);
				currentCard = -1;
				postamble = index;
				break;
			}
			if (!line.startsWith("## major-")) {
				continue;
			}
			addCardSection(lines, currentCard, index, meanings);
			if (firstCard < 0) {
				firstCard = index;
			}
			currentCard = index;
		}
		addCardSection(lines, currentCard, postamble, meanings);
		if (meanings.size() != MajorArcana.all().size()
			|| !meanings.keySet().containsAll(MajorArcana.all())) {
			throw new IllegalArgumentException("All major arcana prompt entries are required");
		}
		return new CardPromptParts(
			String.join("\n", lines.subList(0, firstCard)).strip(),
			Map.copyOf(meanings),
			String.join("\n", lines.subList(postamble, lines.size())).strip()
		);
	}

	private void addCardSection(
		List<String> lines,
		int start,
		int end,
		Map<String, String> meanings
	) {
		if (start < 0) {
			return;
		}
		String heading = lines.get(start);
		int separator = heading.indexOf(" / ");
		if (separator < 0) {
			throw new IllegalArgumentException("Tarot card prompt entry is invalid");
		}
		String cardId = heading.substring("## ".length(), separator);
		String body = String.join("\n", lines.subList(start + 1, end)).strip();
		String section = String.join("\n", lines.subList(start, end)).strip();
		if (!MajorArcana.contains(cardId) || body.isBlank()
			|| meanings.put(cardId, section) != null) {
			throw new IllegalArgumentException("Tarot card prompt entry is invalid");
		}
	}

	private record PromptPart(String version, String content) {
	}

	private record CardPromptParts(
		String preamble,
		Map<String, String> meanings,
		String postamble
	) {

		String select(List<String> cardIds) {
			if (cardIds == null || cardIds.isEmpty()) {
				throw new IllegalArgumentException("Selected tarot cards are required");
			}
			List<String> selected = new ArrayList<>();
			if (!preamble.isBlank()) {
				selected.add(preamble);
			}
			for (String cardId : cardIds) {
				String meaning = meanings.get(cardId);
				if (meaning == null) {
					throw new IllegalArgumentException("Unknown tarot card prompt entry");
				}
				selected.add(meaning);
			}
			if (!postamble.isBlank()) {
				selected.add(postamble);
			}
			return String.join("\n", selected);
		}
	}
}
