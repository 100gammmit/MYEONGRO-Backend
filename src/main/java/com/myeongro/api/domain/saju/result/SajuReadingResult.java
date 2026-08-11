package com.myeongro.api.domain.saju.result;

import java.util.List;

import com.myeongro.api.domain.reading.service.ReadingMode;

public record SajuReadingResult(
	ReadingMode readingMode,
	String title,
	String summary,
	List<SajuReadingSection> natalSections,
	AnnualReading annualReading,
	QuestionReading questionReading,
	List<String> guidance,
	String disclaimer
) {
	public SajuReadingResult {
		natalSections = natalSections == null ? null : List.copyOf(natalSections);
		guidance = guidance == null ? null : List.copyOf(guidance);
	}

	public SajuReadingResult(
		String title,
		String summary,
		List<SajuReadingSection> natalSections,
		AnnualReading annualReading,
		QuestionReading questionReading,
		List<String> guidance,
		String disclaimer
	) {
		this(
			ReadingMode.STANDARD, title, summary, natalSections,
			annualReading, questionReading, guidance, disclaimer
		);
	}

	public record AnnualReading(
		int year,
		String heading,
		String body,
		List<String> evidenceKeys
	) {
		public AnnualReading {
			evidenceKeys = evidenceKeys == null ? null : List.copyOf(evidenceKeys);
		}
	}

	public record QuestionReading(
		String focusArea,
		String heading,
		String body,
		List<String> evidenceKeys
	) {
		public QuestionReading {
			evidenceKeys = evidenceKeys == null ? null : List.copyOf(evidenceKeys);
		}
	}
}
