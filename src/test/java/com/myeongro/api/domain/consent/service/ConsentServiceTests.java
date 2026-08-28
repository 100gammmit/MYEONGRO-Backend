package com.myeongro.api.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEntity;
import com.myeongro.api.domain.consent.repository.ConsentRepository;

class ConsentServiceTests {

	private static final UUID USER_ID = UUID.fromString(
		"3b413be2-2b81-4802-8c6a-f868a85d8d83"
	);
	private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

	@Test
	void returnsCurrentUserConsentStatus() {
		ConsentRepository repository = org.mockito.Mockito.mock(ConsentRepository.class);
		when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(currentConsent()));

		assertThat(service(repository).getUserStatus(USER_ID).hasAcceptedRequired()).isTrue();
	}

	@Test
	void requiresReacceptanceWhenTermsVersionChanges() {
		ConsentRepository repository = org.mockito.Mockito.mock(ConsentRepository.class);
		when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(currentConsent()));

		assertThat(service(repository, "2026-08-28").getUserStatus(USER_ID).hasAcceptedRequired())
			.isFalse();
	}

	@Test
	void createsOneUserOwnedConsentRow() {
		ConsentRepository repository = org.mockito.Mockito.mock(ConsentRepository.class);
		when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());
		when(repository.save(org.mockito.ArgumentMatchers.any(ConsentEntity.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		var accepted = service(repository).acceptRequiredForUser(
			USER_ID,
			ConsentDocumentType.required()
		);

		assertThat(accepted).hasSize(3);
		assertThat(accepted).extracting(item -> item.acceptedAt()).containsOnly(NOW);
	}

	@Test
	void rejectsIncompleteRequiredConsent() {
		assertThatThrownBy(() -> service(org.mockito.Mockito.mock(ConsentRepository.class))
			.acceptRequiredForUser(USER_ID, List.of(ConsentDocumentType.TERMS)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private ConsentService service(ConsentRepository repository) {
		return service(repository, "2026-06-10");
	}

	private ConsentService service(ConsentRepository repository, String termsVersion) {
		return new ConsentService(
			repository,
			Map.of(
				ConsentDocumentType.TERMS, termsVersion,
				ConsentDocumentType.PRIVACY, "2026-06-10",
				ConsentDocumentType.SENSITIVE_DATA, "2026-06-10"
			),
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	private ConsentEntity currentConsent() {
		return ConsentEntity.acceptedForUser(
			USER_ID,
			"2026-06-10",
			"2026-06-10",
			"2026-06-10",
			NOW
		);
	}
}
