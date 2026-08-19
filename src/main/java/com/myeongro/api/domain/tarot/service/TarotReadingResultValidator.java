package com.myeongro.api.domain.tarot.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.tarot.result.TarotReadingResult;
import com.myeongro.api.domain.tarot.result.TarotReadingSection;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;

@Component
public class TarotReadingResultValidator {

	public void validate(TarotSpreadType spreadType, TarotReadingResult result) {
		if (spreadType == null || result == null) {
			throw new IllegalArgumentException("Tarot spread and result are required");
		}
		requireText(result.title(), "Tarot reading title is required");
		if (result.readingMode() == null) {
			throw new IllegalArgumentException("Tarot reading mode is required");
		}
		requireText(result.summary(), "Tarot reading summary is required");
		requireText(result.disclaimer(), "Tarot reading disclaimer is required");

		List<TarotReadingSection> sections = result.sections();
		if (sections == null || sections.size() != spreadType.cardCount()) {
			throw new IllegalArgumentException("Tarot section count does not match spread");
		}
		for (int index = 0; index < sections.size(); index++) {
			TarotReadingSection section = sections.get(index);
			if (section == null) {
				throw new IllegalArgumentException("Tarot section is required");
			}
			requireText(section.position(), "Tarot section position is required");
			requireText(section.heading(), "Tarot section heading is required");
			requireText(section.body(), "Tarot section body is required");
			if (!spreadType.positions().get(index).id().equals(section.position())) {
				throw new IllegalArgumentException("Tarot section position order is invalid");
			}
		}

		List<String> guidance = result.guidance();
		if (guidance == null
			|| guidance.size() < spreadType.minGuidanceItems()
			|| guidance.size() > spreadType.maxGuidanceItems()) {
			throw new IllegalArgumentException("Tarot guidance count does not match spread");
		}
		guidance.forEach(item -> requireText(item, "Tarot guidance text is required"));
	}

	private void requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
	}
}
