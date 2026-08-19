package com.myeongro.api.domain.tarot.result;

import java.util.List;

import com.myeongro.api.domain.reading.service.ReadingMode;

public record TarotReadingResult(
	ReadingMode readingMode,
	String title,
	String summary,
	List<TarotReadingSection> sections,
	List<String> guidance,
	String disclaimer
) {
	public TarotReadingResult(
		String title,
		String summary,
		List<TarotReadingSection> sections,
		List<String> guidance,
		String disclaimer
	) {
		this(ReadingMode.STANDARD, title, summary, sections, guidance, disclaimer);
	}
}
