package com.myeongro.api.domain.reading.dto;

import java.util.List;

import com.myeongro.api.domain.reading.service.ReadingMode;

public record ReadingResult(
	ReadingMode readingMode,
	String title,
	String summary,
	List<ReadingSection> sections,
	List<String> guidance,
	String disclaimer
) {
	public ReadingResult(
		String title,
		String summary,
		List<ReadingSection> sections,
		List<String> guidance,
		String disclaimer
	) {
		this(ReadingMode.STANDARD, title, summary, sections, guidance, disclaimer);
	}
}
