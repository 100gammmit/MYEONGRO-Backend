package com.myeongro.api.aipreview;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AiPreviewMarkdownRenderer {

	private static final DateTimeFormatter EXECUTED_AT_FORMAT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

	private final ZoneId zoneId;

	AiPreviewMarkdownRenderer(ZoneId zoneId) {
		this.zoneId = zoneId;
	}

	String render(AiPreviewReport report) {
		Map<String, Object> result = report.result();
		StringBuilder markdown = new StringBuilder();
		appendHeading(markdown, 1, text(result.get("title"), "AI 리딩 결과"));
		appendParagraph(markdown, text(result.get("summary"), null));

		if ("tarot".equals(report.kind())) {
			appendSections(markdown, result.get("sections"));
		} else if ("saju".equals(report.kind())) {
			appendSections(markdown, result.get("natalSections"));
			appendSection(markdown, result.get("annualReading"));
			appendSection(markdown, result.get("questionReading"));
		}

		appendGuidance(markdown, result.get("guidance"));
		appendDisclaimer(markdown, text(result.get("disclaimer"), null));
		appendExecutionMetadata(markdown, report);
		return markdown.toString();
	}

	private void appendSections(StringBuilder markdown, Object value) {
		if (!(value instanceof List<?> sections)) {
			return;
		}
		for (Object section : sections) {
			appendSection(markdown, section);
		}
	}

	private void appendSection(StringBuilder markdown, Object value) {
		if (!(value instanceof Map<?, ?> section)) {
			return;
		}
		appendHeading(markdown, 2, text(section.get("heading"), null));
		appendParagraph(markdown, text(section.get("body"), null));
		appendEvidence(markdown, section.get("evidenceKeys"));
	}

	private void appendEvidence(StringBuilder markdown, Object value) {
		if (!(value instanceof List<?> evidenceKeys) || evidenceKeys.isEmpty()) {
			return;
		}
		markdown.append("근거: ");
		for (int index = 0; index < evidenceKeys.size(); index++) {
			if (index > 0) {
				markdown.append(", ");
			}
			markdown.append('`').append(evidenceKeys.get(index)).append('`');
		}
		markdown.append("\n\n");
	}

	private void appendGuidance(StringBuilder markdown, Object value) {
		if (!(value instanceof List<?> guidance) || guidance.isEmpty()) {
			return;
		}
		appendHeading(markdown, 2, "실천 가이드");
		for (int index = 0; index < guidance.size(); index++) {
			markdown.append(index + 1).append(". ").append(guidance.get(index)).append('\n');
		}
		markdown.append('\n');
	}

	private void appendDisclaimer(StringBuilder markdown, String disclaimer) {
		if (disclaimer == null || disclaimer.isBlank()) {
			return;
		}
		markdown.append("> ").append(disclaimer.replace("\n", " ")).append("\n\n");
	}

	private void appendExecutionMetadata(StringBuilder markdown, AiPreviewReport report) {
		markdown.append("---\n\n");
		appendHeading(markdown, 2, "실행 정보");
		appendMetadata(markdown, "라벨", code(report.label()));
		appendMetadata(markdown, "fixture", code(report.caseId()));
		appendMetadata(markdown, "종류", code(report.kind()));
		if (report.spreadType() != null) {
			appendMetadata(markdown, "스프레드", code(report.spreadType()));
		}
		appendMetadata(markdown, "모델", code(report.model()));
		appendMetadata(markdown, "프롬프트", code(report.promptVersion()));
		appendMetadata(markdown, "프롬프트 SHA-256", code(report.promptSha256()));
		appendMetadata(
			markdown,
			"생성 시간",
			EXECUTED_AT_FORMAT.format(report.executedAt().atZone(zoneId))
		);
		appendMetadata(
			markdown,
			"소요 시간",
			String.format(Locale.ROOT, "%.3f초", report.durationMillis() / 1_000.0)
		);
	}

	private void appendHeading(StringBuilder markdown, int level, String heading) {
		if (heading == null || heading.isBlank()) {
			return;
		}
		markdown.append("#".repeat(level))
			.append(' ')
			.append(heading.replace("\n", " "))
			.append("\n\n");
	}

	private void appendParagraph(StringBuilder markdown, String paragraph) {
		if (paragraph == null || paragraph.isBlank()) {
			return;
		}
		markdown.append(paragraph).append("\n\n");
	}

	private void appendMetadata(StringBuilder markdown, String name, String value) {
		markdown.append("- ").append(name).append(": ").append(value).append('\n');
	}

	private String code(String value) {
		return "`" + value.replace("`", "\\`") + "`";
	}

	private String text(Object value, String fallback) {
		return value instanceof String string && !string.isBlank() ? string : fallback;
	}
}
