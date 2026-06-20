package com.myeongro.api.domain.reading.dto;

import java.util.List;

public record ReadingResult(
	String title,
	String summary,
	List<ReadingSection> sections,
	List<String> guidance,
	String disclaimer
) {
}
