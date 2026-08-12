package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class OpenAiResponseDiagnosticsTests {

	@Test
	void capturesSafeMetadataWithoutResponseContent() {
		ChatResponse response = new ChatResponse(
			List.of(new Generation(
				new AssistantMessage(null),
				ChatGenerationMetadata.builder().finishReason("LENGTH").build()
			)),
			ChatResponseMetadata.builder().usage(new DefaultUsage(900, 1200)).build()
		);

		OpenAiResponseDiagnostics diagnostics = OpenAiResponseDiagnostics.from(response);

		assertThat(diagnostics).isEqualTo(new OpenAiResponseDiagnostics(
			1, "LENGTH", false, 0, 1200
		));
	}

	@Test
	void capturesContentPresenceAndLength() {
		ChatResponse response = new ChatResponse(List.of(new Generation(
			new AssistantMessage("{\"output\":{}}"),
			ChatGenerationMetadata.builder().finishReason("STOP").build()
		)));

		OpenAiResponseDiagnostics diagnostics = OpenAiResponseDiagnostics.from(response);

		assertThat(diagnostics.generationCount()).isEqualTo(1);
		assertThat(diagnostics.finishReason()).isEqualTo("STOP");
		assertThat(diagnostics.contentPresent()).isTrue();
		assertThat(diagnostics.contentLength()).isEqualTo(13);
	}
}
