package com.myeongro.api.domain.consent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEventEntity;
import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.consent.repository.ConsentEventRepository;

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
	void sajuAlsoRequiresTheSajuInputDocument() {
		ConsentEventRepository repository = repositoryWith(
			accepted(ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasVersion()),
			accepted(ConsentDocumentType.TERMS, termsVersion())
		);

		var status = service(repository).getUserStatus(USER_ID, ConsentScope.SAJU);

		assertThat(status.hasAcceptedRequired()).isFalse();
		assertThat(status.acceptedDocumentTypes()).containsExactly(
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
	void recordsOneDocumentAcceptanceAtTheServerTime() {
		ConsentEventRepository repository = repositoryWith();
		when(repository.save(org.mockito.ArgumentMatchers.any(ConsentEventEntity.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		var accepted = service(repository).acceptForUser(
			USER_ID,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER,
			overseasVersion()
		);

		assertThat(accepted.documentType())
			.isEqualTo(ConsentDocumentType.AI_OVERSEAS_TRANSFER);
		assertThat(accepted.documentVersion()).isEqualTo(overseasVersion());
		assertThat(accepted.acceptedAt()).isEqualTo(NOW);
	}

	@Test
	void keepsARepeatedCurrentAcceptanceIdempotent() {
		ConsentEventEntity current = accepted(
			ConsentDocumentType.AI_OVERSEAS_TRANSFER,
			overseasVersion()
		);
		ConsentEventRepository repository = repositoryWith(current);

		var accepted = service(repository).acceptForUser(
			USER_ID,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER,
			overseasVersion()
		);

		assertThat(accepted.acceptedAt()).isEqualTo(NOW);
		verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void rejectsAClientThatReviewedAnOutdatedDocument() {
		ConsentEventRepository repository = repositoryWith();

		assertThatThrownBy(() -> service(repository).acceptForUser(
			USER_ID,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER,
			"outdated"
		)).isInstanceOf(ConsentVersionMismatchException.class);
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
	void rejectsIndependentWithdrawalWithoutACompleteCleanupContract() {
		assertThatThrownBy(() -> service(repositoryWith()).withdrawForUser(
			USER_ID,
			ConsentDocumentType.TERMS
		)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service(repositoryWith()).withdrawForUser(
			USER_ID,
			ConsentDocumentType.SAJU_INPUT
		)).isInstanceOf(IllegalArgumentException.class);
	}

	private ConsentEventRepository repositoryWith(ConsentEventEntity... events) {
		ConsentEventRepository repository =
			org.mockito.Mockito.mock(ConsentEventRepository.class);
		when(repository.findAllByUserIdOrderByOccurredAtDescIdDesc(USER_ID))
			.thenReturn(List.of(events));
		return repository;
	}

	private ConsentService service(ConsentEventRepository repository) {
		return new ConsentService(
			repository,
			Map.of(
				ConsentDocumentType.TERMS, termsVersion(),
				ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasVersion(),
				ConsentDocumentType.SAJU_INPUT, sajuInputVersion()
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

	private String sajuInputVersion() {
		return "draft-2026-09-07";
	}
}
