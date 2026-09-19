package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.saju.model.SajuFocusArea;
import com.myeongro.api.domain.saju.service.SajuCalculationInput;

class ReadingInputFingerprinterTests {

	private static final String SECRET = "test-only-saju-idempotency-secret-32-bytes";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final ReadingInputFingerprinter fingerprinter =
		new ReadingInputFingerprinter(objectMapper, SECRET);

	@Test
	void canonicalizesNestedMapsWhilePreservingArrayOrder() {
		Map<String, Object> firstCard = new LinkedHashMap<>();
		firstCard.put("cardId", "major-00-fool");
		firstCard.put("position", "today");
		Map<String, Object> secondCard = new LinkedHashMap<>();
		secondCard.put("position", "today");
		secondCard.put("cardId", "major-00-fool");

		assertThat(fingerprinter.fingerprint(tarot(List.of(firstCard))))
			.isEqualTo(fingerprinter.fingerprint(tarot(List.of(secondCard))));
		assertThat(fingerprinter.fingerprint(tarot(List.of(
			Map.of("cardId", "major-00-fool"),
			Map.of("cardId", "major-01-magician")
		)))).isNotEqualTo(fingerprinter.fingerprint(tarot(List.of(
			Map.of("cardId", "major-01-magician"),
			Map.of("cardId", "major-00-fool")
		))));
	}

	@Test
	void sajuFingerprintChangesForQuestionFocusOrBirthInputAndIsNotPlainSha256()
		throws Exception {
		SajuCalculationInput original = saju("질문", SajuFocusArea.CAREER, "1992-08-17");
		String fingerprint = fingerprinter.fingerprint(original);

		assertThat(fingerprint)
			.isNotEqualTo(fingerprinter.fingerprint(saju(
				"다른 질문", SajuFocusArea.CAREER, "1992-08-17"
			)))
			.isNotEqualTo(fingerprinter.fingerprint(saju(
				"질문", SajuFocusArea.RELATIONSHIP, "1992-08-17"
			)))
			.isNotEqualTo(fingerprinter.fingerprint(saju(
				"질문", SajuFocusArea.CAREER, "1992-08-18"
			)));

		byte[] canonical = objectMapper.writer()
			.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.writeValueAsBytes(ReadingInputSupport.hashMaterial(
				original.kind(), original.spreadType(), original.schemaVersion(),
				original.idempotencyPayload()
			));
		String plainSha = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
			MessageDigest.getInstance("SHA-256").digest(canonical)
		);
		assertThat(fingerprint).isNotEqualTo(plainSha);
	}

	private NormalizedReadingInput tarot(List<Map<String, Object>> cards) {
		return new NormalizedReadingInput(
			ReadingKind.TAROT, "mind_three_card", ReadingSchemaVersions.TAROT,
			"질문", Map.of("cards", cards)
		);
	}

	private SajuCalculationInput saju(
		String question,
		SajuFocusArea focusArea,
		String birthDate
	) {
		return new SajuCalculationInput(
			ReadingSchemaVersions.SAJU,
			question,
			focusArea,
			Map.of(
				"calendarType", "solar",
				"birthDate", birthDate,
				"birthTimePrecision", "unknown",
				"luckDirectionBasis", "unspecified"
			)
		);
	}
}
