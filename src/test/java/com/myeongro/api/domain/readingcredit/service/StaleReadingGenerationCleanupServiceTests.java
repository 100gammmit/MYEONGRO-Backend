package com.myeongro.api.domain.readingcredit.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.readingcredit.ReadingCreditTestFixtures;
import com.myeongro.api.domain.readingcredit.repository.ReadingCreditRepository;

class StaleReadingGenerationCleanupServiceTests {

	@Test
	void delegatesConfiguredStaleDurationToAtomicCleanup() {
		ReadingCreditRepository repository = org.mockito.Mockito.mock(
			ReadingCreditRepository.class
		);
		var properties = ReadingCreditTestFixtures.properties();
		when(repository.failStaleGenerations(properties.staleAfter())).thenReturn(2);
		var service = new StaleReadingGenerationCleanupService(repository, properties);

		service.failStaleGenerations();

		verify(repository).failStaleGenerations(properties.staleAfter());
	}
}
