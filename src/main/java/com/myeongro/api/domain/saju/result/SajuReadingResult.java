package com.myeongro.api.domain.saju.result;

import java.util.List;

public record SajuReadingResult(
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
