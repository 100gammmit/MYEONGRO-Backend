package com.myeongro.api.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.consent.dto.ConsentAcceptance;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEntity;
import com.myeongro.api.domain.consent.repository.ConsentRepository;

class ConsentServiceTests {

	private static final UUID GUEST_ID =
		UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d");
	private static final UUID USER_ID =
		UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private static final Map<ConsentDocumentType, String> VERSIONS = Map.of(
		ConsentDocumentType.TERMS, "2026-06-10",
		ConsentDocumentType.PRIVACY, "2026-06-10",
		ConsentDocumentType.SENSITIVE_DATA, "2026-06-10"
	);

	@Test
	void reportsOnlyDocumentsAcceptedAtTheCurrentVersion() {
		ConsentRepository repository = mock(ConsentRepository.class);
		when(repository.findAllByGuestSessionId(GUEST_ID)).thenReturn(List.of(
			consent(ConsentDocumentType.TERMS, "2026-06-10"),
			consent(ConsentDocumentType.PRIVACY, "old")
		));
		ConsentService service = service(repository);

		ConsentStatus status = service.getStatus(GUEST_ID);

		assertThat(status.acceptedDocumentTypes())
			.containsExactly(ConsentDocumentType.TERMS);
		assertThat(status.requiredDocumentTypes())
			.containsExactlyElementsOf(ConsentDocumentType.required());
		assertThat(status.hasAcceptedRequired()).isFalse();
	}

	@Test
	void reportsUserDocumentsAcceptedAtTheCurrentVersion() {
		ConsentRepository repository = mock(ConsentRepository.class);
		when(repository.findAllByUserId(USER_ID)).thenReturn(List.of(
			ConsentEntity.forUser(
				USER_ID,
				ConsentDocumentType.TERMS,
				"2026-06-10",
				Instant.parse("2026-06-11T00:00:00Z")
			),
			ConsentEntity.forUser(
				USER_ID,
				ConsentDocumentType.PRIVACY,
				"old",
				Instant.parse("2026-06-11T00:00:00Z")
			)
		));
		ConsentService service = service(repository);

		ConsentStatus status = service.getUserStatus(USER_ID);

		assertThat(status.acceptedDocumentTypes())
			.containsExactly(ConsentDocumentType.TERMS);
		assertThat(status.hasAcceptedRequired()).isFalse();
	}

	@Test
	void rejectsAcceptanceWhenAnyRequiredDocumentIsMissing() {
		ConsentRepository repository = mock(ConsentRepository.class);
		ConsentService service = service(repository);

		assertThatThrownBy(() -> service.acceptRequired(
			GUEST_ID,
			List.of(ConsentDocumentType.TERMS, ConsentDocumentType.PRIVACY)
		))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Missing required consent: sensitive-data");

		verify(repository, never()).saveAll(anyList());
	}

	@Test
	void savesMissingCurrentVersionConsentsAtOneServerOwnedTimestamp() {
		ConsentRepository repository = mock(ConsentRepository.class);
		when(repository.findAllByGuestSessionId(GUEST_ID))
			.thenReturn(List.of(
				consentAt(ConsentDocumentType.TERMS, "2026-06-10", "2026-06-15T00:00:00Z"),
				consentAt(ConsentDocumentType.PRIVACY, "2026-06-10", "2026-06-15T00:00:00Z"),
				consentAt(ConsentDocumentType.SENSITIVE_DATA, "2026-06-10", "2026-06-15T00:00:00Z")
			));
		ConsentService service = service(repository);

		List<ConsentAcceptance> saved = service.acceptRequired(
			GUEST_ID,
			ConsentDocumentType.required()
		);

		assertThat(saved).hasSize(3);
		assertThat(saved)
			.extracting(ConsentAcceptance::acceptedAt)
			.containsOnly(Instant.parse("2026-06-15T00:00:00Z"));
		assertThat(saved)
			.extracting(ConsentAcceptance::documentVersion)
			.containsOnly("2026-06-10");
		verify(repository, times(3)).insertGuestConsentIfAbsent(
			org.mockito.ArgumentMatchers.eq(GUEST_ID),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.eq("2026-06-10"),
			org.mockito.ArgumentMatchers.eq(Instant.parse("2026-06-15T00:00:00Z"))
		);
		verify(repository, never()).saveAll(anyList());
	}

	@Test
	void savesUserConsentsIdempotentlyAtOneServerOwnedTimestamp() {
		ConsentRepository repository = mock(ConsentRepository.class);
		when(repository.findAllByUserId(USER_ID))
			.thenReturn(List.of(
				ConsentEntity.forUser(
					USER_ID,
					ConsentDocumentType.TERMS,
					"2026-06-10",
					Instant.parse("2026-06-15T00:00:00Z")
				),
				ConsentEntity.forUser(
					USER_ID,
					ConsentDocumentType.PRIVACY,
					"2026-06-10",
					Instant.parse("2026-06-15T00:00:00Z")
				),
				ConsentEntity.forUser(
					USER_ID,
					ConsentDocumentType.SENSITIVE_DATA,
					"2026-06-10",
					Instant.parse("2026-06-15T00:00:00Z")
				)
			));
		ConsentService service = service(repository);

		List<ConsentAcceptance> saved = service.acceptRequiredForUser(
			USER_ID,
			ConsentDocumentType.required()
		);

		assertThat(saved).hasSize(3);
		verify(repository, times(3)).insertUserConsentIfAbsent(
			org.mockito.ArgumentMatchers.eq(USER_ID),
			org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.eq("2026-06-10"),
			org.mockito.ArgumentMatchers.eq(Instant.parse("2026-06-15T00:00:00Z"))
		);
		verify(repository, never()).saveAll(anyList());
	}

	@Test
	void preservesExistingAcceptanceInsteadOfSavingItAgain() {
		ConsentRepository repository = mock(ConsentRepository.class);
		ConsentEntity existing = ConsentEntity.forGuest(
			GUEST_ID,
			ConsentDocumentType.TERMS,
			"2026-06-10",
			Instant.parse("2026-06-11T00:00:00Z")
		);
		when(repository.findAllByGuestSessionId(GUEST_ID)).thenReturn(List.of(
			existing,
			consentAt(ConsentDocumentType.PRIVACY, "2026-06-10", "2026-06-15T00:00:00Z"),
			consentAt(
				ConsentDocumentType.SENSITIVE_DATA,
				"2026-06-10",
				"2026-06-15T00:00:00Z"
			)
		));
		ConsentService service = service(repository);

		List<ConsentAcceptance> saved = service.acceptRequired(
			GUEST_ID,
			ConsentDocumentType.required()
		);

		assertThat(saved).filteredOn(
			acceptance -> acceptance.documentType() == ConsentDocumentType.TERMS
		).singleElement()
			.extracting(ConsentAcceptance::acceptedAt)
			.isEqualTo(Instant.parse("2026-06-11T00:00:00Z"));
	}

	private ConsentService service(ConsentRepository repository) {
		return new ConsentService(
			repository,
			VERSIONS,
			Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC)
		);
	}

	private ConsentEntity consent(ConsentDocumentType type, String version) {
		return consentAt(type, version, "2026-06-11T00:00:00Z");
	}

	private ConsentEntity consentAt(
		ConsentDocumentType type,
		String version,
		String acceptedAt
	) {
		return ConsentEntity.forGuest(
			GUEST_ID,
			type,
			version,
			Instant.parse(acceptedAt)
		);
	}
}
