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
	public ConsentStatus getStatus(UUID guestSessionId) {
		List<ConsentEntity> stored = repository.findAllByGuestSessionId(guestSessionId);
		List<ConsentDocumentType> accepted = ConsentDocumentType.required().stream()
			.filter(type -> stored.stream().anyMatch(consent ->
				consent.getDocumentType() == type
					&& versions.get(type).equals(consent.getDocumentVersion())
			))
			.toList();
		return new ConsentStatus(
			accepted,
			ConsentDocumentType.required(),
			accepted.size() == ConsentDocumentType.required().size()
		);
	}

	@Transactional
	public List<ConsentAcceptance> acceptRequired(
		UUID guestSessionId,
		List<ConsentDocumentType> acceptedDocumentTypes
	) {
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

		Instant acceptedAt = clock.instant();
		ConsentDocumentType.required().forEach(type ->
			repository.insertGuestConsentIfAbsent(
				guestSessionId,
				type.value(),
				versions.get(type),
				acceptedAt
			)
		);

		List<ConsentEntity> stored = repository.findAllByGuestSessionId(guestSessionId);
		Map<ConsentDocumentType, ConsentEntity> current = currentVersionByType(stored);

		return ConsentDocumentType.required().stream()
			.map(current::get)
			.map(ConsentAcceptance::from)
			.toList();
	}

	private Map<ConsentDocumentType, ConsentEntity> currentVersionByType(
		List<ConsentEntity> stored
	) {
		Map<ConsentDocumentType, ConsentEntity> current = new EnumMap<>(
			ConsentDocumentType.class
		);
		stored.stream()
			.filter(consent -> versions.get(consent.getDocumentType())
				.equals(consent.getDocumentVersion()))
			.forEach(consent -> current.putIfAbsent(
				consent.getDocumentType(),
				consent
			));
		return current;
	}
}
