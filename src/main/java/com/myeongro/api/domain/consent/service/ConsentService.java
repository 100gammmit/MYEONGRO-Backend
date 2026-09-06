package com.myeongro.api.domain.consent.service;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.domain.consent.dto.ConsentAcceptance;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentAction;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEventEntity;
import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.consent.repository.ConsentEventRepository;

@Service
public class ConsentService {

	private final ConsentEventRepository repository;
	private final Map<ConsentDocumentType, String> versions;
	private final Clock clock;

	@Autowired
	public ConsentService(
		ConsentEventRepository repository,
		@Value("${app.consent.versions.terms}") String termsVersion,
		@Value("${app.consent.versions.ai-overseas-transfer}") String overseasTransferVersion,
		@Value("${app.consent.versions.saju-input}") String sajuInputVersion
	) {
		this(
			repository,
			Map.of(
				ConsentDocumentType.TERMS, termsVersion,
				ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasTransferVersion,
				ConsentDocumentType.SAJU_INPUT, sajuInputVersion
			),
			Clock.systemUTC()
		);
	}

	ConsentService(
		ConsentEventRepository repository,
		Map<ConsentDocumentType, String> versions,
		Clock clock
	) {
		this.repository = repository;
		this.versions = Map.copyOf(versions);
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ConsentStatus getUserStatus(UUID userId, ConsentScope scope) {
		Map<ConsentDocumentType, ConsentEventEntity> latest = latestEvents(userId);
		List<ConsentDocumentType> required = scope.requiredDocuments();
		List<ConsentDocumentType> accepted = required.stream()
			.filter(type -> isCurrentAcceptance(latest.get(type), type))
			.toList();
		return new ConsentStatus(accepted, required, accepted.size() == required.size());
	}

	@Transactional(readOnly = true)
	public boolean hasAccepted(UUID userId, ConsentScope scope) {
		return getUserStatus(userId, scope).hasAcceptedRequired();
	}

	@Transactional
	public ConsentAcceptance acceptForUser(
		UUID userId,
		ConsentDocumentType documentType,
		String documentVersion
	) {
		validateActive(documentType);
		String currentVersion = versions.get(documentType);
		if (!currentVersion.equals(documentVersion)) {
			throw new ConsentVersionMismatchException();
		}

		ConsentEventEntity current = latestEvents(userId).get(documentType);
		if (isCurrentAcceptance(current, documentType)) {
			return ConsentAcceptance.from(current);
		}

		ConsentEventEntity accepted = repository.save(ConsentEventEntity.accepted(
			userId,
			documentType,
			currentVersion,
			clock.instant()
		));
		return ConsentAcceptance.from(accepted);
	}

	@Transactional
	public void withdrawForUser(UUID userId, ConsentDocumentType documentType) {
		validateActive(documentType);
		if (!documentType.canBeWithdrawn()) {
			throw new IllegalArgumentException(
				"Consent cannot be withdrawn independently: " + documentType.value()
			);
		}

		ConsentEventEntity current = latestEvents(userId).get(documentType);
		if (current == null || current.getAction() == ConsentAction.WITHDRAWN) {
			return;
		}
		repository.save(ConsentEventEntity.withdrawn(
			userId,
			documentType,
			current.getDocumentVersion(),
			clock.instant()
		));
	}

	private Map<ConsentDocumentType, ConsentEventEntity> latestEvents(UUID userId) {
		Map<ConsentDocumentType, ConsentEventEntity> latest =
			new EnumMap<>(ConsentDocumentType.class);
		for (ConsentEventEntity event
			: repository.findAllByUserIdOrderByOccurredAtDescIdDesc(userId)) {
			latest.putIfAbsent(event.getDocumentType(), event);
		}
		return latest;
	}

	private boolean isCurrentAcceptance(
		ConsentEventEntity event,
		ConsentDocumentType documentType
	) {
		return event != null
			&& event.getAction() == ConsentAction.ACCEPTED
			&& versions.get(documentType).equals(event.getDocumentVersion());
	}

	private void validateActive(ConsentDocumentType documentType) {
		if (!ConsentDocumentType.active().contains(documentType)) {
			throw new IllegalArgumentException(
				"Consent document is not independently accepted: " + documentType.value()
			);
		}
	}
}
