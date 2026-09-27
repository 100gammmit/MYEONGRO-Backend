package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	private static final String TAROT_SECRET = "test-only-tarot-idempotency-secret-32-bytes";
	private static final String SAJU_SECRET = "test-only-saju-idempotency-secret-32-bytes";
	private static final String SELECTION_SECRET = "test-only-tarot-selection-secret-32-bytes";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final ReadingInputFingerprinter fingerprinter =
		new ReadingInputFingerprinter(objectMapper, TAROT_SECRET, SAJU_SECRET, SELECTION_SECRET);

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

		assertThat(fingerprint).isNotEqualTo(plainSha256(original));
	}

	@Test
	void tarotFingerprintChangesForQuestionOrChoiceOptionsAndIsNotPlainSha256()
		throws Exception {
		List<Map<String, Object>> cards = List.of(Map.of("cardId", "major-00-fool"));
		NormalizedReadingInput original = tarot("질문", cards, null);
		String fingerprint = fingerprinter.fingerprint(original);

		assertThat(fingerprinter.fingerprint(tarot("질문", cards, null)))
			.isEqualTo(fingerprint);
		assertThat(fingerprint)
			.isNotEqualTo(fingerprinter.fingerprint(tarot("다른 질문", cards, null)))
			.isNotEqualTo(fingerprinter.fingerprint(tarot(
				"질문", cards, Map.of("a", "이직", "b", "잔류")
			)));
		assertThat(fingerprinter.fingerprint(tarot(
			"질문", cards, Map.of("a", "이직", "b", "잔류")
		))).isNotEqualTo(fingerprinter.fingerprint(tarot(
			"질문", cards, Map.of("a", "이직", "b", "휴식")
		)));
		assertThat(fingerprint).isNotEqualTo(plainSha256(original));
	}

	@Test
	void signsEachReadingKindOnlyWithItsOwnIdempotencySecret() {
		NormalizedReadingInput tarot = tarot(List.of(Map.of("cardId", "major-00-fool")));
		SajuCalculationInput saju = saju("질문", SajuFocusArea.CAREER, "1992-08-17");
		ReadingInputFingerprinter otherTarotSecret = new ReadingInputFingerprinter(
			objectMapper, "another-tarot-idempotency-secret-32-bytes", SAJU_SECRET,
			SELECTION_SECRET
		);
		ReadingInputFingerprinter otherSajuSecret = new ReadingInputFingerprinter(
			objectMapper, TAROT_SECRET, "another-saju-idempotency-secret-32-bytes",
			SELECTION_SECRET
		);

		assertThat(otherTarotSecret.fingerprint(tarot))
			.isNotEqualTo(fingerprinter.fingerprint(tarot));
		assertThat(otherTarotSecret.fingerprint(saju))
			.isEqualTo(fingerprinter.fingerprint(saju));
		assertThat(otherSajuSecret.fingerprint(saju))
			.isNotEqualTo(fingerprinter.fingerprint(saju));
		assertThat(otherSajuSecret.fingerprint(tarot))
			.isEqualTo(fingerprinter.fingerprint(tarot));
	}

	@Test
	void rejectsShortOrSharedSecrets() {
		assertThatThrownBy(() -> new ReadingInputFingerprinter(
			objectMapper, "too-short", SAJU_SECRET, SELECTION_SECRET
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("app.reading.tarot-idempotency-secret");
		assertThatThrownBy(() -> new ReadingInputFingerprinter(
			objectMapper, TAROT_SECRET, null, SELECTION_SECRET
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("app.reading.saju-idempotency-secret");
		assertThatThrownBy(() -> new ReadingInputFingerprinter(
			objectMapper, SAJU_SECRET, SAJU_SECRET, SELECTION_SECRET
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("distinct");
		assertThatThrownBy(() -> new ReadingInputFingerprinter(
			objectMapper, SELECTION_SECRET, SAJU_SECRET, SELECTION_SECRET
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("distinct");
		assertThatThrownBy(() -> new ReadingInputFingerprinter(
			objectMapper, TAROT_SECRET, SELECTION_SECRET, SELECTION_SECRET
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("distinct");
	}

	private String plainSha256(ReadingRequestInput input) throws Exception {
		byte[] canonical = objectMapper.writer()
			.with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
			.writeValueAsBytes(ReadingInputSupport.hashMaterial(
				input.kind(), input.spreadType(), input.schemaVersion(),
				input.idempotencyPayload()
			));
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
			MessageDigest.getInstance("SHA-256").digest(canonical)
		);
	}

	private NormalizedReadingInput tarot(
		String question,
		List<Map<String, Object>> cards,
		Map<String, Object> choiceOptions
	) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("question", question);
		payload.put("cards", cards);
		if (choiceOptions != null) {
			payload.put("choiceOptions", choiceOptions);
		}
		return new NormalizedReadingInput(
			ReadingKind.TAROT, "choice_five_card", ReadingSchemaVersions.TAROT,
			question, payload
		);
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
