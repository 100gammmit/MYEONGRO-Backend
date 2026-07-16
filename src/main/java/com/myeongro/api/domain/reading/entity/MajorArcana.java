package com.myeongro.api.domain.reading.entity;

import java.util.List;
import java.util.Set;

public final class MajorArcana {

	private static final List<String> CARDS = List.of(
		"major-00-fool",
		"major-01-magician",
		"major-02-high-priestess",
		"major-03-empress",
		"major-04-emperor",
		"major-05-hierophant",
		"major-06-lovers",
		"major-07-chariot",
		"major-08-strength",
		"major-09-hermit",
		"major-10-wheel-of-fortune",
		"major-11-justice",
		"major-12-hanged-man",
		"major-13-death",
		"major-14-temperance",
		"major-15-devil",
		"major-16-tower",
		"major-17-star",
		"major-18-moon",
		"major-19-sun",
		"major-20-judgement",
		"major-21-world"
	);
	private static final Set<String> CARD_IDS = Set.copyOf(CARDS);

	private MajorArcana() {
	}

	public static boolean contains(String cardId) {
		return CARD_IDS.contains(cardId);
	}

	public static List<String> all() {
		return CARDS;
	}
}
