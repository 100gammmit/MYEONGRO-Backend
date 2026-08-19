package com.myeongro.api.domain.saju.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class SajuPromptCatalog {

	private final PromptPart common;
	private final PromptPart interpretation;
	private final PromptPart report;

	public SajuPromptCatalog(
		@Value("${app.reading.prompts.saju.common}") Resource common,
		@Value("${app.reading.prompts.saju.interpretation}") Resource interpretation,
		@Value("${app.reading.prompts.saju.reports.birth-annual-question}") Resource report
	) {
		this.common = read(common);
		this.interpretation = read(interpretation);
		this.report = read(report);
	}

	public String prompt() {
		return String.join(
			"\n\n",
			common.content(),
			interpretation.content(),
			report.content()
		);
	}

	public String version() {
		return String.join(
			"+",
			common.version(),
			interpretation.version(),
			report.version()
		);
	}

	private PromptPart read(Resource resource) {
		try {
			String filename = resource.getFilename();
			if (filename == null || !filename.endsWith(".md")) {
				throw new IllegalArgumentException("Versioned saju prompt filename is required");
			}
			String content = resource.getContentAsString(StandardCharsets.UTF_8);
			if (content.isBlank()) {
				throw new IllegalArgumentException("Saju prompt is empty");
			}
			return new PromptPart(filename.substring(0, filename.length() - 3), content);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot read saju prompt", exception);
		}
	}

	private record PromptPart(String version, String content) {
	}
}
