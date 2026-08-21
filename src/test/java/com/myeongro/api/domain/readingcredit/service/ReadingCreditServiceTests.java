package com.myeongro.api.domain.readingcredit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.readingcredit.ReadingCreditTestFixtures;
import com.myeongro.api.domain.readingcredit.repository.ReadingCreditRepository;
import com.myeongro.api.domain.readingcredit.repository.ReadingCreditSnapshot;

class ReadingCreditServiceTests {

	@Test
	void returnsBalancesCostsAndNextKoreanMidnight() {
		UUID userId = UUID.randomUUID();
		ReadingCreditRepository repository = org.mockito.Mockito.mock(
			ReadingCreditRepository.class
		);
		when(repository.getStatus(userId, 10))
			.thenReturn(new ReadingCreditSnapshot(7, 5, true));
		Clock clock = Clock.fixed(
			Instant.parse("2026-08-21T14:30:00Z"), ZoneOffset.UTC
		);
		ReadingCreditService service = new ReadingCreditService(
			repository, ReadingCreditTestFixtures.properties(), clock
		);

		var response = service.getStatus(userId);

		assertThat(response.dailyFreeGrant()).isEqualTo(10);
		assertThat(response.balance().free()).isEqualTo(7);
		assertThat(response.balance().paid()).isEqualTo(5);
		assertThat(response.balance().total()).isEqualTo(12);
		assertThat(response.nextResetAt()).isEqualTo(
			Instant.parse("2026-08-21T15:00:00Z")
		);
		assertThat(response.generationInProgress()).isTrue();
		assertThat(response.costs().tarot()).containsExactlyInAnyOrderEntriesOf(
			java.util.Map.of(
				"daily_one_card", 1,
				"mind_three_card", 2,
				"relationship_three_card", 2,
				"choice_five_card", 3
			)
		);
		assertThat(response.costs().saju()).isEqualTo(4);
		verify(repository).getStatus(userId, 10);
	}
}
