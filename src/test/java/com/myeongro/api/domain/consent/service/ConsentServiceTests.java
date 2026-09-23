package com.myeongro.api.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEventEntity;
import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.consent.repository.ConsentEventRepository;
import com.myeongro.api.domain.consent.repository.ConsentTransitionLock;

class ConsentServiceTests {

	private static final UUID USER_ID = UUID.fromString(
		"3b413be2-2b81-4802-8c6a-f868a85d8d83"
	);
	private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

	@Test
	void tarotRequiresTermsAndOverseasTransferOnly() {
		ConsentEventRepository repository = repositoryWith(
			accepted(ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasVersion()),
			accepted(ConsentDocumentType.TERMS, termsVersion())
		);

		var status = service(repository).getUserStatus(USER_ID, ConsentScope.TAROT);

		assertThat(status.hasAcceptedRequired()).isTrue();
		assertThat(status.requiredDocumentTypes()).containsExactly(
			ConsentDocumentType.TERMS,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER
		);
	}

	@Test
	void sajuRequiresTheSameTermsAndOverseasTransferDocumentsAsTarot() {
		ConsentEventRepository repository = repositoryWith(
			accepted(ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasVersion()),
			accepted(ConsentDocumentType.TERMS, termsVersion())
		);

		var status = service(repository).getUserStatus(USER_ID, ConsentScope.SAJU);

		assertThat(status.hasAcceptedRequired()).isTrue();
		assertThat(status.acceptedDocumentTypes()).containsExactly(
			ConsentDocumentType.TERMS,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER
		);
		assertThat(status.requiredDocumentTypes()).containsExactly(
			ConsentDocumentType.TERMS,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER
		);
	}

	@Test
	void withdrawnOrOutdatedDocumentsAreNotCurrent() {
		ConsentEventRepository repository = repositoryWith(
			ConsentEventEntity.withdrawn(
				USER_ID,
				ConsentDocumentType.AI_OVERSEAS_TRANSFER,
				overseasVersion(),
				NOW.plusSeconds(1)
			),
			accepted(ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasVersion()),
			accepted(ConsentDocumentType.TERMS, "2026-06-10")
		);

		var status = service(repository).getUserStatus(USER_ID, ConsentScope.TAROT);

		assertThat(status.acceptedDocumentTypes()).isEmpty();
		assertThat(status.hasAcceptedRequired()).isFalse();
	}

	@Test
	void recordsEveryRequiredDocumentAtOneServerTime() {
		ConsentEventRepository repository = repositoryWith();
		when(repository.save(org.mockito.ArgumentMatchers.any(ConsentEventEntity.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		var status = service(repository).completeRequiredForUser(
			USER_ID,
			ConsentScope.TAROT,
			tarotVersions()
		);

		assertThat(status.hasAcceptedRequired()).isTrue();
		verify(repository).save(org.mockito.ArgumentMatchers.argThat(event ->
			event.getDocumentType() == ConsentDocumentType.TERMS
				&& event.getDocumentVersion().equals(termsVersion())
				&& event.getOccurredAt().equals(NOW)
		));
		verify(repository).save(org.mockito.ArgumentMatchers.argThat(event ->
			event.getDocumentType() == ConsentDocumentType.AI_OVERSEAS_TRANSFER
				&& event.getDocumentVersion().equals(overseasVersion())
				&& event.getOccurredAt().equals(NOW)
		));
	}

	@Test
	void locksRequiredDocumentsInScopeOrderBeforeReadingAndRecording() {
		ConsentEventRepository repository = repositoryWith();
		ConsentTransitionLock transitionLock = mock(ConsentTransitionLock.class);
		when(repository.save(org.mockito.ArgumentMatchers.any(ConsentEventEntity.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		service(repository, transitionLock).completeRequiredForUser(
			USER_ID,
			ConsentScope.TAROT,
			tarotVersions()
		);

		var ordered = inOrder(transitionLock, repository);
		ordered.verify(transitionLock).lock(USER_ID, ConsentDocumentType.TERMS);
		ordered.verify(transitionLock).lock(
			USER_ID,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER
		);
		ordered.verify(repository).findAllByUserIdOrderByOccurredAtDescIdDesc(USER_ID);
		ordered.verify(repository, org.mockito.Mockito.times(2))
			.save(org.mockito.ArgumentMatchers.any(ConsentEventEntity.class));
	}

	@Test
	void keepsARepeatedBatchOfCurrentAcceptancesIdempotent() {
		ConsentEventRepository repository = repositoryWith(
			accepted(ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasVersion()),
			accepted(ConsentDocumentType.TERMS, termsVersion())
		);

		var status = service(repository).completeRequiredForUser(
			USER_ID,
			ConsentScope.TAROT,
			tarotVersions()
		);

		assertThat(status.hasAcceptedRequired()).isTrue();
		verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void recordsExactlyTheSharedRequiredDocumentsForSaju() {
		ConsentEventRepository repository = repositoryWith();
		when(repository.save(org.mockito.ArgumentMatchers.any(ConsentEventEntity.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		var status = service(repository).completeRequiredForUser(
			USER_ID,
			ConsentScope.SAJU,
			tarotVersions()
		);

		assertThat(status.hasAcceptedRequired()).isTrue();
		verify(repository).save(org.mockito.ArgumentMatchers.argThat(event ->
			event.getDocumentType() == ConsentDocumentType.TERMS
				&& event.getDocumentVersion().equals(termsVersion())
		));
		verify(repository).save(org.mockito.ArgumentMatchers.argThat(event ->
			event.getDocumentType() == ConsentDocumentType.AI_OVERSEAS_TRANSFER
				&& event.getDocumentVersion().equals(overseasVersion())
		));
	}

	@Test
	void validatesEveryVersionBeforeLockingOrWritingTheBatch() {
		ConsentEventRepository repository = repositoryWith();
		ConsentTransitionLock transitionLock = mock(ConsentTransitionLock.class);

		assertThatThrownBy(() -> service(repository, transitionLock).completeRequiredForUser(
			USER_ID,
			ConsentScope.TAROT,
			Map.of(
				"terms", termsVersion(),
				"ai-overseas-transfer", "outdated"
			)
		)).isInstanceOf(ConsentVersionMismatchException.class);

		verify(transitionLock, never()).lock(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		);
		verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejectsAnIncompleteOrUnexpectedDocumentSet() {
		ConsentService service = service(repositoryWith());

		assertThatThrownBy(() -> service.completeRequiredForUser(
			USER_ID,
			ConsentScope.TAROT,
			Map.of("terms", termsVersion())
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.completeRequiredForUser(
			USER_ID,
			ConsentScope.SAJU,
			Map.of(
				"terms", termsVersion(),
				"ai-overseas-transfer", overseasVersion(),
				"saju-input", "retired-version"
			)
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void recordsAWithdrawEventForFeatureConsent() {
		ConsentEventRepository repository = repositoryWith(
			accepted(ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasVersion())
		);
		when(repository.save(org.mockito.ArgumentMatchers.any(ConsentEventEntity.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		service(repository).withdrawForUser(
			USER_ID,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER
		);

		verify(repository).save(org.mockito.ArgumentMatchers.argThat(event ->
			event.getDocumentType() == ConsentDocumentType.AI_OVERSEAS_TRANSFER
				&& event.getAction().name().equals("WITHDRAWN")
				&& event.getOccurredAt().equals(NOW)
		));
	}

	@Test
	void locksTheUserDocumentBeforeReadingAndRecordingWithdrawal() {
		ConsentEventRepository repository = repositoryWith(
			accepted(ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasVersion())
		);
		ConsentTransitionLock transitionLock = mock(ConsentTransitionLock.class);
		when(repository.save(org.mockito.ArgumentMatchers.any(ConsentEventEntity.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		service(repository, transitionLock).withdrawForUser(
			USER_ID,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER
		);

		var ordered = inOrder(transitionLock, repository);
		ordered.verify(transitionLock).lock(
			USER_ID,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER
		);
		ordered.verify(repository).findAllByUserIdOrderByOccurredAtDescIdDesc(USER_ID);
		ordered.verify(repository).save(org.mockito.ArgumentMatchers.any(ConsentEventEntity.class));
	}

	@Test
	void rejectsIndependentWithdrawalWithoutACompleteCleanupContract() {
		assertThatThrownBy(() -> service(repositoryWith()).withdrawForUser(
			USER_ID,
			ConsentDocumentType.TERMS
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsTheRetiredSajuInputDocumentType() {
		assertThatThrownBy(() -> ConsentDocumentType.fromValue("saju-input"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unknown consent document type");
	}

	private ConsentEventRepository repositoryWith(ConsentEventEntity... events) {
		ConsentEventRepository repository =
			org.mockito.Mockito.mock(ConsentEventRepository.class);
		when(repository.findAllByUserIdOrderByOccurredAtDescIdDesc(USER_ID))
			.thenReturn(List.of(events));
		return repository;
	}

	private ConsentService service(ConsentEventRepository repository) {
		return service(repository, mock(ConsentTransitionLock.class));
	}

	private ConsentService service(
		ConsentEventRepository repository,
		ConsentTransitionLock transitionLock
	) {
		return new ConsentService(
			repository,
			transitionLock,
			Map.of(
				ConsentDocumentType.TERMS, termsVersion(),
				ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasVersion()
			),
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	private ConsentEventEntity accepted(
		ConsentDocumentType documentType,
		String documentVersion
	) {
		return ConsentEventEntity.accepted(USER_ID, documentType, documentVersion, NOW);
	}

	private String termsVersion() {
		return "2026-08-28";
	}

	private String overseasVersion() {
		return "draft-2026-09-07";
	}

	private Map<String, String> tarotVersions() {
		return Map.of(
			"terms", termsVersion(),
			"ai-overseas-transfer", overseasVersion()
		);
	}
}
