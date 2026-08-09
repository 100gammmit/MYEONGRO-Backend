package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.reading.entity.ReadingKind;

class AiPreviewLabelFactoryTests {

	private static final Clock FIXED_CLOCK = Clock.fixed(
		Instant.parse("2026-08-10T06:23:45Z"),
		ZoneId.of("Asia/Seoul")
	);

	@Test
	void createsTimestampedTarotLabel() {
		AiPreviewLabelFactory factory = new AiPreviewLabelFactory(FIXED_CLOCK);

		assertThat(factory.create(ReadingKind.TAROT)).isEqualTo("20260810-152345-tarot");
	}

	@Test
	void createsTimestampedSajuLabel() {
		AiPreviewLabelFactory factory = new AiPreviewLabelFactory(FIXED_CLOCK);

		assertThat(factory.create(ReadingKind.SAJU)).isEqualTo("20260810-152345-saju");
	}
}
