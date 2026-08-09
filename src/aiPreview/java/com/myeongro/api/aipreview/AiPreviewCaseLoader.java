package com.myeongro.api.aipreview;

import java.io.IOException;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.entity.ReadingKind;

final class AiPreviewCaseLoader {

	private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

	private final ObjectMapper objectMapper;

	AiPreviewCaseLoader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	AiPreviewCase load(String kindValue, String caseId) {
		if (!SAFE_NAME.matcher(kindValue).matches() || !SAFE_NAME.matcher(caseId).matches()) {
			throw new IllegalArgumentException("Preview kind and case must use lowercase letters, digits, or hyphens");
		}
		ReadingKind requestedKind = ReadingKind.fromValue(kindValue);
		ClassPathResource resource = new ClassPathResource(
			"ai-preview/cases/" + kindValue + "/" + caseId + ".json"
		);
		try {
			AiPreviewCase previewCase = objectMapper.readValue(
				resource.getInputStream(),
				AiPreviewCase.class
			);
			validate(previewCase, requestedKind, caseId);
			return previewCase;
		} catch (IOException exception) {
			throw new IllegalArgumentException("Cannot load AI preview case: " + caseId, exception);
		}
	}

	private void validate(
		AiPreviewCase previewCase,
		ReadingKind requestedKind,
		String requestedCaseId
	) {
		if (!requestedCaseId.equals(previewCase.id()) || requestedKind != previewCase.kind()) {
			throw new IllegalArgumentException("AI preview case metadata does not match its path");
		}
		if (previewCase.question() == null || previewCase.question().isBlank()) {
			throw new IllegalArgumentException("AI preview question is required");
		}
		if (requestedKind == ReadingKind.TAROT && previewCase.spreadType() == null) {
			throw new IllegalArgumentException("Tarot preview spread type is required");
		}
		if (requestedKind == ReadingKind.SAJU && previewCase.spreadType() != null) {
			throw new IllegalArgumentException("Saju preview must not define a tarot spread type");
		}
	}
}
