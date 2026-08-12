package com.myeongro.api.domain.reading.service;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

public record OpenAiResponseDiagnostics(
	int generationCount,
	String finishReason,
	boolean contentPresent,
	int contentLength,
	Integer completionTokens
) {

	public static OpenAiResponseDiagnostics unavailable() {
		return new OpenAiResponseDiagnostics(0, "unavailable", false, 0, null);
	}

	public static OpenAiResponseDiagnostics from(ChatResponse response) {
		if (response == null) {
			return unavailable();
		}
		Generation generation = response.getResult();
		String content = generation == null || generation.getOutput() == null
			? null
			: generation.getOutput().getText();
		String finishReason = generation == null || generation.getMetadata() == null
			? "unavailable"
			: safeFinishReason(generation.getMetadata().getFinishReason());
		Integer completionTokens = response.getMetadata() == null
			|| response.getMetadata().getUsage() == null
			? null
			: response.getMetadata().getUsage().getCompletionTokens();
		return new OpenAiResponseDiagnostics(
			response.getResults().size(),
			finishReason,
			content != null && !content.isBlank(),
			content == null ? 0 : content.length(),
			completionTokens
		);
	}

	private static String safeFinishReason(String finishReason) {
		return finishReason == null || finishReason.isBlank() ? "unavailable" : finishReason;
	}
}
