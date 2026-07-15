package com.myeongro.api.domain.consent.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.domain.consent.dto.ConsentAcceptance;
import com.myeongro.api.domain.consent.dto.ConsentStatus;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEntity;
import com.myeongro.api.domain.consent.repository.ConsentRepository;

@Service
public class ConsentService {

	private final ConsentRepository repository;
	private final Map<ConsentDocumentType, String> versions;
	private final Clock clock;

	@Autowired
	public ConsentService(
		ConsentRepository repository,
		@Value("${app.consent.versions.terms}") String termsVersion,
		@Value("${app.consent.versions.privacy}") String privacyVersion,
		@Value("${app.consent.versions.sensitive-data}") String sensitiveDataVersion
	) {
		this(
			repository,
			Map.of(
				ConsentDocumentType.TERMS, termsVersion,
				ConsentDocumentType.PRIVACY, privacyVersion,
				ConsentDocumentType.SENSITIVE_DATA, sensitiveDataVersion
			),
			Clock.systemUTC()
		);
	}

	ConsentService(
		ConsentRepository repository,
		Map<ConsentDocumentType, String> versions,
		Clock clock
	) {
		this.repository = repository;
		this.versions = Map.copyOf(versions);
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ConsentStatus getUserStatus(UUID userId) {
		Optional<ConsentEntity> stored = repository.findByUserId(userId);
		return toStatus(stored);
	}

	private ConsentStatus toStatus(Optional<ConsentEntity> stored) {
		List<ConsentDocumentType> accepted = ConsentDocumentType.required().stream()
			.filter(type -> stored
				.map(consent -> versions.get(type).equals(consent.versionOf(type)))
				.orElse(false))
			.toList();
		return new ConsentStatus(
			accepted,
			ConsentDocumentType.required(),
			accepted.size() == ConsentDocumentType.required().size()
		);
	}

	@Transactional
	public List<ConsentAcceptance> acceptRequiredForUser(
		UUID userId,
		List<ConsentDocumentType> acceptedDocumentTypes
	) {
		validateAcceptedRequired(acceptedDocumentTypes);
		Instant acceptedAt = clock.instant();
		ConsentEntity stored = repository.findByUserId(userId)
			.map(consent -> acceptCurrentVersions(consent, acceptedAt))
			.orElseGet(() -> ConsentEntity.acceptedForUser(
				userId,
				termsVersion(),
				privacyVersion(),
				sensitiveDataVersion(),
				acceptedAt
			));
		ConsentEntity saved = repository.save(stored);

		return ConsentDocumentType.required().stream()
			.map(type -> ConsentAcceptance.from(saved, type))
			.toList();
	}

	private void validateAcceptedRequired(List<ConsentDocumentType> acceptedDocumentTypes) {
		List<ConsentDocumentType> accepted = acceptedDocumentTypes == null
			? List.of()
			: acceptedDocumentTypes.stream().distinct().toList();
		for (ConsentDocumentType required : ConsentDocumentType.required()) {
			if (!accepted.contains(required)) {
				throw new IllegalArgumentException(
					"Missing required consent: " + required.value()
				);
			}
		}
		if (accepted.size() != ConsentDocumentType.required().size()) {
			throw new IllegalArgumentException("Only required consent documents are allowed");
		}
	}

	private ConsentEntity acceptCurrentVersions(
		ConsentEntity consent,
		Instant acceptedAt
	) {
		if (!consent.hasAcceptedCurrentVersions(
			termsVersion(),
			privacyVersion(),
			sensitiveDataVersion()
		)) {
			consent.acceptOutdatedVersions(
				termsVersion(),
				privacyVersion(),
				sensitiveDataVersion(),
				acceptedAt
			);
		}
		return consent;
	}

	private String termsVersion() {
		return versions.get(ConsentDocumentType.TERMS);
	}

	private String privacyVersion() {
		return versions.get(ConsentDocumentType.PRIVACY);
	}

	private String sensitiveDataVersion() {
		return versions.get(ConsentDocumentType.SENSITIVE_DATA);
	}
}
