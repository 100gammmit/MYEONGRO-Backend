package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class ReadingSafetyPromptTests {

	@Test
	void exposesVersionAndContentFromVersionedMarkdown() {
		ReadingSafetyPrompt prompt = new ReadingSafetyPrompt(
			resource("synthetic-safety-v3.md", "synthetic safety policy")
		);

		assertThat(prompt.version()).isEqualTo("synthetic-safety-v3");
		assertThat(prompt.content()).isEqualTo("synthetic safety policy");
	}

	@Test
	void rejectsEmptyOrNonMarkdownResource() {
		assertThatThrownBy(() -> new ReadingSafetyPrompt(resource("safety.md", " ")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ReadingSafetyPrompt(resource("safety.txt", "policy")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private ByteArrayResource resource(String filename, String content) {
		return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
	}
}
