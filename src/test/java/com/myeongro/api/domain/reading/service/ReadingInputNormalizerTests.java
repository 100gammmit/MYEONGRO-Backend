package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.controller.ChoiceOptionsRequest;
import com.myeongro.api.domain.reading.controller.ReadingCreateRequest;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import com.myeongro.api.domain.saju.model.SajuBirthProfileRequest;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

class ReadingInputNormalizerTests {

	private final ReadingInputNormalizer normalizer = new ReadingInputNormalizer(catalog());

	@ParameterizedTest
	@MethodSource("spreadRequests")
	void assignsOrderedCardIdsToCanonicalPositions(
		TarotSpreadType spread,
		List<String> cards,
		ChoiceOptionsRequest choices
	) {
		NormalizedReadingInput normalized = normalizer.normalizeTarot(
			tarotRequest(spread, choices), spread, cards
		);

		assertThat(normalized.spreadType()).isEqualTo(spread);
		assertThat(normalized.schemaVersion()).isEqualTo(ReadingSchemaVersions.TAROT);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> storedCards =
			(List<Map<String, Object>>) normalized.payload().get("cards");
		assertThat(storedCards).extracting(card -> card.get("cardId"))
			.containsExactlyElementsOf(cards);
		assertThat(storedCards).extracting(card -> card.get("position"))
			.containsExactlyElementsOf(spread.positions().stream()
				.map(position -> position.id())
				.toList());
		assertThat(storedCards).allSatisfy(card ->
			assertThat(card).containsEntry("reversed", false));
		assertThat(normalized.hashMaterial().keySet()).containsExactly(
			"kind", "spreadType", "schemaVersion", "inputPayload"
		);
	}

	@Test
	void normalizesExactSajuInputAsSchemaVersionTwo() {
		NormalizedReadingInput normalized = normalizer.normalize(sajuRequest(
			new SajuBirthProfileRequest(
				"solar", "1992-08-17", "14:30", "exact",
				"11", "11680", "female"
			)
		));

		assertThat(normalized.schemaVersion()).isEqualTo(ReadingSchemaVersions.SAJU);
		assertThat(normalized.spreadType()).isNull();
		assertThat(normalized.payload()).containsEntry("question", "올해 이직운이 궁금해요")
			.containsEntry("focusArea", "career");
		assertThat(normalized.payload()).containsOnlyKeys("question", "focusArea", "birthProfile");
		@SuppressWarnings("unchecked")
		Map<String, Object> profile =
			(Map<String, Object>) normalized.payload().get("birthProfile");
		assertThat(profile).containsEntry("birthTime", "14:30")
			.containsEntry("birthTimePrecision", "exact")
			.containsEntry("provinceCode", "11")
			.containsEntry("cityCode", "11680")
			.doesNotContainKeys("latitude", "longitude", "pillars");
		assertThat(normalized.hashMaterial()).containsEntry("schemaVersion", 2);
		assertThat(normalized.hashMaterial().get("inputPayload")).isEqualTo(normalized.payload());
	}

	@Test
	void omitsBirthTimeWhenPrecisionIsUnknown() {
		NormalizedReadingInput normalized = normalizer.normalize(sajuRequest(
			new SajuBirthProfileRequest(
				"solar", "1992-08-17", null, "unknown",
				"36", "36110", "unspecified"
			)
		));

		@SuppressWarnings("unchecked")
		Map<String, Object> profile =
			(Map<String, Object>) normalized.payload().get("birthProfile");
		assertThat(profile).doesNotContainKey("birthTime")
			.containsEntry("birthTimePrecision", "unknown");
	}

	@Test
	void acceptsApproximateBirthTimeWithoutClientOwnedUncertaintyRange() {
		NormalizedReadingInput normalized = normalizer.normalize(sajuRequest(
			new SajuBirthProfileRequest(
				"solar", "1992-08-17", "14:00", "approximate",
				"11", "11680", "unspecified"
			)
		));

		@SuppressWarnings("unchecked")
		Map<String, Object> profile =
			(Map<String, Object>) normalized.payload().get("birthProfile");
		assertThat(profile).containsEntry("birthTime", "14:00")
			.containsEntry("birthTimePrecision", "approximate")
			.doesNotContainKeys("birthTimeWindow", "uncertaintyMinutes");
	}

	@ParameterizedTest
	@MethodSource("invalidSajuRequests")
	void rejectsInvalidSajuContracts(
		SajuBirthProfileRequest profile,
		String focusArea,
		String expectedCode,
		String expectedField
	) {
		ReadingCreateRequest request = new ReadingCreateRequest(
			"saju", null, "질문", UUID.randomUUID(), null, null, profile, focusArea
		);

		assertThatThrownBy(() -> normalizer.normalize(request))
			.isInstanceOfSatisfying(InvalidReadingRequestException.class, exception -> {
				assertThat(exception.getCode()).isEqualTo(expectedCode);
				assertThat(exception.getField()).isEqualTo(expectedField);
			});
	}

	@Test
	void rejectsTarotFieldsInSajuAndSajuFieldsInTarot() {
		ReadingCreateRequest sajuWithSpread = new ReadingCreateRequest(
			"saju", "daily_one_card", "질문", UUID.randomUUID(), null, null,
			validProfile(), "career"
		);
		assertThatThrownBy(() -> normalizer.normalize(sajuWithSpread))
			.isInstanceOfSatisfying(InvalidReadingRequestException.class, exception ->
				assertThat(exception.getField()).isEqualTo("spreadType"));

		ReadingCreateRequest tarotWithProfile = new ReadingCreateRequest(
			"tarot", "daily_one_card", "질문", UUID.randomUUID(), List.of(1), null,
			validProfile(), null
		);
		assertThatThrownBy(() -> normalizer.normalizeTarot(
			tarotWithProfile, TarotSpreadType.DAILY_ONE_CARD, cards(1)
		)).isInstanceOfSatisfying(InvalidReadingRequestException.class, exception ->
			assertThat(exception.getField()).isEqualTo("birthProfile"));
	}

	@Test
	void acceptsChoiceOptionsOnlyForChoiceSpread() {
		assertThatThrownBy(() -> normalizer.normalizeTarot(tarotRequest(
			TarotSpreadType.DAILY_ONE_CARD,
			new ChoiceOptionsRequest("A", "B")
		), TarotSpreadType.DAILY_ONE_CARD, List.of("major-00-fool")))
			.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> normalizer.normalizeTarot(tarotRequest(
			TarotSpreadType.CHOICE_FIVE_CARD,
			null
		), TarotSpreadType.CHOICE_FIVE_CARD, cards(5)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsWrongCardCountDuplicateAndUnknownCards() {
		assertThatThrownBy(() -> normalizer.normalizeTarot(tarotRequest(
			TarotSpreadType.MIND_THREE_CARD,
			null
		), TarotSpreadType.MIND_THREE_CARD, cards(1)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> normalizer.normalizeTarot(tarotRequest(
			TarotSpreadType.MIND_THREE_CARD,
			null
		), TarotSpreadType.MIND_THREE_CARD,
			List.of("major-00-fool", "major-00-fool", "major-01-magician")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> normalizer.normalizeTarot(tarotRequest(
			TarotSpreadType.DAILY_ONE_CARD,
			null
		), TarotSpreadType.DAILY_ONE_CARD, List.of("not-a-card")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	static java.util.stream.Stream<Arguments> spreadRequests() {
		return java.util.stream.Stream.of(
			Arguments.of(TarotSpreadType.DAILY_ONE_CARD, cards(1), null),
			Arguments.of(TarotSpreadType.MIND_THREE_CARD, cards(3), null),
			Arguments.of(TarotSpreadType.RELATIONSHIP_THREE_CARD, cards(3), null),
			Arguments.of(
				TarotSpreadType.CHOICE_FIVE_CARD,
				cards(5),
				new ChoiceOptionsRequest("현재 일을 유지한다", "새 기회를 준비한다")
			)
		);
	}

	static java.util.stream.Stream<Arguments> invalidSajuRequests() {
		return java.util.stream.Stream.of(
			Arguments.of(profile("lunar", "1992-08-17", "14:30", "exact", "11", "11680", "female"), "career", "UNSUPPORTED_CALENDAR_TYPE", "birthProfile.calendarType"),
			Arguments.of(profile("solar", "1899-12-31", "14:30", "exact", "11", "11680", "female"), "career", "UNSUPPORTED_BIRTH_YEAR", "birthProfile.birthDate"),
			Arguments.of(profile("solar", "1992-02-30", "14:30", "exact", "11", "11680", "female"), "career", "INVALID_BIRTH_DATE", "birthProfile.birthDate"),
			Arguments.of(profile("solar", "1992-08-17", null, "exact", "11", "11680", "female"), "career", "INVALID_BIRTH_TIME", "birthProfile.birthTime"),
			Arguments.of(profile("solar", "1992-08-17", "14:30", "unknown", "11", "11680", "female"), "career", "INVALID_BIRTH_TIME", "birthProfile.birthTime"),
			Arguments.of(profile("solar", "1992-08-17", "14:30", "rough", "11", "11680", "female"), "career", "INVALID_BIRTH_TIME_PRECISION", "birthProfile.birthTimePrecision"),
			Arguments.of(profile("solar", "1992-08-17", "14:30", "exact", "26", "11680", "female"), "career", "INVALID_BIRTH_PLACE", "birthProfile.cityCode"),
			Arguments.of(profile("solar", "1992-08-17", "14:30", "exact", "11", "11680", "other"), "career", "INVALID_LUCK_DIRECTION_BASIS", "birthProfile.luckDirectionBasis"),
			Arguments.of(profile("solar", "1992-08-17", "14:30", "exact", "11", "11680", "female"), "health", "INVALID_FOCUS_AREA", "focusArea")
		);
	}

	private ReadingCreateRequest tarotRequest(
		TarotSpreadType spread,
		ChoiceOptionsRequest choices
	) {
		return new ReadingCreateRequest(
			"tarot", spread.value(), " 질문 ", UUID.randomUUID(),
			java.util.Collections.nCopies(spread.cardCount(), 1), choices, null, null
		);
	}

	private ReadingCreateRequest sajuRequest(SajuBirthProfileRequest profile) {
		return new ReadingCreateRequest(
			"saju", null, " 올해 이직운이 궁금해요 ", UUID.randomUUID(),
			null, null, profile, "career"
		);
	}

	private static SajuBirthProfileRequest validProfile() {
		return profile("solar", "1992-08-17", "14:30", "exact", "11", "11680", "female");
	}

	private static SajuBirthProfileRequest profile(
		String calendarType,
		String birthDate,
		String birthTime,
		String precision,
		String provinceCode,
		String cityCode,
		String basis
	) {
		return new SajuBirthProfileRequest(
			calendarType, birthDate, birthTime, precision, provinceCode, cityCode, basis
		);
	}

	private static List<String> cards(int count) {
		return List.of(
			"major-00-fool",
			"major-01-magician",
			"major-02-high-priestess",
			"major-03-empress",
			"major-04-emperor"
		).subList(0, count);
	}

	private static SajuBirthPlaceCatalog catalog() {
		return new SajuBirthPlaceCatalog(
			new ObjectMapper(),
			new ClassPathResource("saju/birth-places/kr-admin-v1.json")
		);
	}
}
