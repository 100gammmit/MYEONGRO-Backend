package com.myeongro.api.domain.saju.result;

import java.util.List;

import com.myeongro.api.domain.reading.service.ReadingMode;

public record SajuReadingResult(
	ReadingMode readingMode,
	Boolean questionRedirected,
	String title,
	String summary,
	List<SajuReadingSection> natalSections,
	AnnualReading annualReading,
	QuestionReading questionReading,
	String disclaimer
) {
	public SajuReadingResult {
		natalSections = natalSections == null ? null : List.copyOf(natalSections);
	}

	public SajuReadingResult(
		String title,
		String summary,
		List<SajuReadingSection> natalSections,
		AnnualReading annualReading,
		QuestionReading questionReading,
		String disclaimer
	) {
		this(
			ReadingMode.STANDARD, false, title, summary, natalSections,
			annualReading, questionReading, disclaimer
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
		String heading,
		String body,
		List<String> evidenceKeys
	) {
		public QuestionReading {
			evidenceKeys = evidenceKeys == null ? null : List.copyOf(evidenceKeys);
		}
	}
}
