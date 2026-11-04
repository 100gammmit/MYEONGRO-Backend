package com.myeongro.api.domain.saju.annual;

import java.util.List;
import com.myeongro.api.domain.reading.service.ReadingMode;

public record SajuAnnualResult(
	String reportType, String detailLevel, int targetYear, ReadingMode readingMode, Boolean questionRedirected,
	String title, Section annualOverview, List<Month> months, List<Period> periods,
	Section questionReading, String summary, String disclaimer
) {
	public SajuAnnualResult {
		months = months == null ? null : List.copyOf(months);
		periods = periods == null ? null : List.copyOf(periods);
	}
	public record Section(String heading, String body, List<String> evidenceKeys) {
		public Section { evidenceKeys = evidenceKeys == null ? null : List.copyOf(evidenceKeys); }
	}
	public record Month(int month, String heading, String body, List<String> evidenceKeys) {
		public Month { evidenceKeys = evidenceKeys == null ? null : List.copyOf(evidenceKeys); }
	}
	public record Period(String periodId, String heading, String body, List<String> evidenceKeys) {
		public Period { evidenceKeys = evidenceKeys == null ? null : List.copyOf(evidenceKeys); }
	}
}
