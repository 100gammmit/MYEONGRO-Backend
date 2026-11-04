package com.myeongro.api.domain.saju.annual;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import com.myeongro.api.domain.reading.service.ReadingSafetyPrompt;

/** Explicitly imported by production configuration and the isolated Preview application. */
public class SajuAnnualPromptCatalog {
	private final String prompt;
	private final String version;
	public SajuAnnualPromptCatalog(ReadingSafetyPrompt safety,
		@Value("classpath:prompts/saju/common-ko-v11.md") Resource common,
		@Value("classpath:prompts/saju/interpretation/interpretation-guide-ko-v7.md") Resource interpretation,
		@Value("classpath:prompts/saju/reports/annual-monthly/annual-monthly-ko-v5.md") Resource report) {
		try {
			List<Resource> parts = List.of(common, interpretation, report);
			var contents = new java.util.ArrayList<String>();
			var versions = new java.util.ArrayList<String>();
			for (Resource part : parts) {
				String filename = part.getFilename();
				String content = part.getContentAsString(StandardCharsets.UTF_8);
				if (filename == null || !filename.endsWith(".md") || content.isBlank()) {
					throw new IllegalArgumentException("Versioned annual prompt required");
				}
				contents.add(content); versions.add(filename.substring(0, filename.length() - 3));
			}
			contents.add(safety.content()); versions.add(safety.version());
			prompt = String.join("\n\n", contents); version = String.join("+", versions);
		} catch (IOException exception) { throw new IllegalStateException("Cannot read annual prompt", exception); }
	}
	public String prompt() { return prompt; }
	public String version() { return version; }
}
