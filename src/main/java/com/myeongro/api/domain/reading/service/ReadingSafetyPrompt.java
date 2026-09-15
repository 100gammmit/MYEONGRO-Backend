package com.myeongro.api.domain.reading.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class ReadingSafetyPrompt {

	private final String version;
	private final String content;

	public ReadingSafetyPrompt(
		@Value("${app.reading.prompts.safety}") Resource resource
	) {
		try {
			String filename = resource.getFilename();
			if (filename == null || !filename.endsWith(".md")) {
				throw new IllegalArgumentException("Versioned reading safety prompt filename is required");
			}
			String prompt = resource.getContentAsString(StandardCharsets.UTF_8);
			if (prompt.isBlank()) {
				throw new IllegalArgumentException("Reading safety prompt is empty");
			}
			this.version = filename.substring(0, filename.length() - 3);
			this.content = prompt;
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot read reading safety prompt", exception);
		}
	}

	public String version() {
		return version;
	}

	public String content() {
		return content;
	}
}
