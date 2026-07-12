package com.myeongro.api.domain.reading.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.dto.ReadingSection;

@Component
public class TarotReadingResultValidator {

	private static final List<String> SECTION_HEADINGS = List.of("과거", "현재", "조언");

	public void validate(ReadingResult result) {
		if (result == null) {
			throw new IllegalArgumentException("Tarot reading result is required");
		}
		requireText(result.title(), "Tarot reading title is required");
		requireText(result.summary(), "Tarot reading summary is required");
		requireText(result.disclaimer(), "Tarot reading disclaimer is required");

		List<ReadingSection> sections = result.sections();
		if (sections == null || sections.size() != SECTION_HEADINGS.size()) {
			throw new IllegalArgumentException("Tarot reading must contain exactly three sections");
		}
		for (int index = 0; index < sections.size(); index++) {
			ReadingSection section = sections.get(index);
			if (section == null) {
				throw new IllegalArgumentException("Tarot section text is required");
			}
			requireText(section.heading(), "Tarot section text is required");
			requireText(section.body(), "Tarot section text is required");
			if (!section.heading().contains(SECTION_HEADINGS.get(index))) {
				throw new IllegalArgumentException("Tarot section heading order is invalid");
			}
		}

		List<String> guidance = result.guidance();
		if (guidance == null || guidance.size() < 2 || guidance.size() > 3) {
			throw new IllegalArgumentException(
				"Tarot reading guidance must contain two or three items"
			);
		}
		for (String item : guidance) {
			requireText(item, "Tarot guidance text is required");
		}
	}

	private void requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
	}
}
