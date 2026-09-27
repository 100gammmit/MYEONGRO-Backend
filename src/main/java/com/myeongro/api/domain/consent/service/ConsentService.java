package com.myeongro.api.domain.consent.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentAction;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEventEntity;
import com.myeongro.api.domain.consent.entity.ConsentScope;
import com.myeongro.api.domain.consent.repository.ConsentEventRepository;
import com.myeongro.api.domain.consent.repository.ConsentTransitionLock;

@Service
public class ConsentService {

	private final ConsentEventRepository repository;
	private final ConsentTransitionLock transitionLock;
	private final Map<ConsentDocumentType, String> versions;
	private final Clock clock;

	@Autowired
	public ConsentService(
		ConsentEventRepository repository,
		ConsentTransitionLock transitionLock,
		@Value("${app.consent.versions.terms}") String termsVersion,
		@Value("${app.consent.versions.ai-overseas-transfer}") String overseasTransferVersion
	) {
		this(
			repository,
			transitionLock,
			Map.of(
				ConsentDocumentType.TERMS, termsVersion,
				ConsentDocumentType.AI_OVERSEAS_TRANSFER, overseasTransferVersion
			),
			Clock.systemUTC()
		);
	}

	ConsentService(
		ConsentEventRepository repository,
		ConsentTransitionLock transitionLock,
		Map<ConsentDocumentType, String> versions,
		Clock clock
	) {
		this.repository = repository;
		this.transitionLock = transitionLock;
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
	public void acceptTermsForSignup(UUID userId, String submittedVersion) {
		ConsentDocumentType documentType = ConsentDocumentType.TERMS;
		if (!versions.get(documentType).equals(submittedVersion)) {
			throw new ConsentVersionMismatchException();
		}
		transitionLock.lock(userId, documentType);
		ConsentEventEntity current = latestEvents(userId).get(documentType);
		if (!isCurrentAcceptance(current, documentType)) {
			repository.save(ConsentEventEntity.accepted(
				userId,
				documentType,
				submittedVersion,
				clock.instant()
			));
		}
	}

	@Transactional
	public ConsentStatus completeRequiredForUser(
		UUID userId,
		ConsentScope scope,
		Map<String, String> submittedVersions
	) {
		List<ConsentDocumentType> required = scope.requiredDocuments();
		Map<ConsentDocumentType, String> requested = new EnumMap<>(ConsentDocumentType.class);
		for (Map.Entry<String, String> entry : submittedVersions.entrySet()) {
			ConsentDocumentType documentType = ConsentDocumentType.fromValue(entry.getKey());
			validateActive(documentType);
			requested.put(documentType, entry.getValue());
		}
		if (requested.isEmpty() || !required.containsAll(requested.keySet())) {
			throw new IllegalArgumentException("Only required consent documents may be submitted");
		}
		for (Map.Entry<ConsentDocumentType, String> entry : requested.entrySet()) {
			if (!versions.get(entry.getKey()).equals(entry.getValue())) {
				throw new ConsentVersionMismatchException();
			}
		}

		for (ConsentDocumentType documentType : required) {
			transitionLock.lock(userId, documentType);
		}
		Map<ConsentDocumentType, ConsentEventEntity> latest = latestEvents(userId);
		List<ConsentDocumentType> missing = required.stream()
			.filter(type -> !isCurrentAcceptance(latest.get(type), type))
			.toList();
		if (!requested.keySet().containsAll(missing)) {
			throw new IllegalArgumentException("All missing consent documents must be submitted");
		}
		Instant acceptedAt = clock.instant();
		for (ConsentDocumentType documentType : missing) {
			repository.save(ConsentEventEntity.accepted(
				userId,
				documentType,
				versions.get(documentType),
				acceptedAt
			));
		}
		return new ConsentStatus(required, required, true);
	}

	@Transactional
	public void withdrawForUser(UUID userId, ConsentDocumentType documentType) {
		validateActive(documentType);
		if (!documentType.canBeWithdrawn()) {
			throw new IllegalArgumentException(
				"Consent cannot be withdrawn independently: " + documentType.value()
			);
		}

		transitionLock.lock(userId, documentType);
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
