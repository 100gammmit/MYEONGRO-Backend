package com.myeongro.api.dailycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.tarot.model.MajorArcana;

class DailyCardContentValidatorApplicationTests {

	@Test
	void validatesARequestedSyntheticPilotWithoutProductionPromptCopy() {
		List<String> cards = List.of("major-00-fool", "major-16-tower");

		var report = DailyCardContentValidatorApplication.validate(
			content(cards),
			DailyCardContentValidatorApplication.ValidationMode.PILOT,
			cards
		);

		assertThat(report.valid()).isTrue();
		assertThat(report.cardCount()).isEqualTo(2);
		assertThat(report.entryCount()).isEqualTo(12);
		assertThat(report.cards()).containsExactlyElementsOf(cards);
	}

	@Test
	void pilotRequiresOneToTenSupportedCardsAndTheExactRequestedSet() {
		assertThatThrownBy(() -> validatePilot(content(List.of("major-00-fool")), List.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("between 1 and 10 cards");

		List<String> elevenCards = MajorArcana.all().subList(0, 11);
		assertThatThrownBy(() -> validatePilot(content(elevenCards), elevenCards))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("between 1 and 10 cards");

		assertThatThrownBy(() -> validatePilot(
			content(List.of("major-00-fool")), List.of("major-16-tower")
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("card set");
	}

	@Test
	void releaseRequiresAllMajorArcanaAndRejectsACardSubsetProperty() {
		var report = DailyCardContentValidatorApplication.validate(
			content(MajorArcana.all()),
			DailyCardContentValidatorApplication.ValidationMode.RELEASE,
			List.of()
		);

		assertThat(report.cardCount()).isEqualTo(22);
		assertThat(report.entryCount()).isEqualTo(132);
		assertThatThrownBy(() -> DailyCardContentValidatorApplication.validate(
			content(MajorArcana.all()),
			DailyCardContentValidatorApplication.ValidationMode.RELEASE,
			List.of("major-00-fool")
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("does not accept a card subset");
	}

	@Test
	void rejectsActionGuidanceAndANonCanonicalDisclaimer() {
		List<DailyCardContentValidatorApplication.Content> actionContent =
			new ArrayList<>(content(List.of("major-00-fool")));
		var first = actionContent.getFirst();
		actionContent.set(0, new DailyCardContentValidatorApplication.Content(
			first.cardId(), first.variantIndex(), first.title(), first.today(),
			List.of("작은 일부터 시작해보는 것도 좋아요."), first.disclaimer()
		));
		assertThatThrownBy(() -> validatePilot(actionContent, List.of("major-00-fool")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Action language");

		List<DailyCardContentValidatorApplication.Content> disclaimerContent =
			new ArrayList<>(content(List.of("major-00-fool")));
		first = disclaimerContent.getFirst();
		disclaimerContent.set(0, new DailyCardContentValidatorApplication.Content(
			first.cardId(), first.variantIndex(), first.title(), first.today(),
			first.guidance(), "synthetic disclaimer"
		));
		assertThatThrownBy(() -> validatePilot(disclaimerContent, List.of("major-00-fool")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("disclaimer");
	}

	@Test
	void rejectsMissingVariantsAndMultiSentenceGuidance() {
		List<DailyCardContentValidatorApplication.Content> missingVariant =
			new ArrayList<>(content(List.of("major-00-fool")));
		missingVariant.removeLast();
		assertThatThrownBy(() -> validatePilot(missingVariant, List.of("major-00-fool")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("entry count");

		List<DailyCardContentValidatorApplication.Content> multiSentence =
			new ArrayList<>(content(List.of("major-00-fool")));
		var first = multiSentence.getFirst();
		multiSentence.set(0, new DailyCardContentValidatorApplication.Content(
			first.cardId(), first.variantIndex(), first.title(), first.today(),
			List.of("오늘의 기대는 소중해요. 천천히 느껴도 괜찮아요."), first.disclaimer()
		));
		assertThatThrownBy(() -> validatePilot(multiSentence, List.of("major-00-fool")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("exactly one declarative sentence");
	}

	private void validatePilot(
		List<DailyCardContentValidatorApplication.Content> content,
		List<String> cards
	) {
		DailyCardContentValidatorApplication.validate(
			content, DailyCardContentValidatorApplication.ValidationMode.PILOT, cards
		);
	}

	private List<DailyCardContentValidatorApplication.Content> content(List<String> cards) {
		return cards.stream().flatMap(cardId -> IntStream.range(0, 6).mapToObj(index ->
			new DailyCardContentValidatorApplication.Content(
				cardId,
				index,
				"synthetic title " + cardId + " " + index,
				new DailyCardContentValidatorApplication.Today(
					"synthetic heading " + cardId + " " + index,
					DailyCardContentValidatorApplication.cardName(cardId)
						+ " 카드는 synthetic evidence " + index + "를 보여줘요."
				),
				List.of("오늘의 기대를 편안하게 받아들여도 괜찮아요."),
				DailyCardContentValidatorApplication.DISCLAIMER
			)
		)).toList();
	}
}
