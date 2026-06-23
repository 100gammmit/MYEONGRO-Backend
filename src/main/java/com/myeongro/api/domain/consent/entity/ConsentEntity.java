package com.myeongro.api.domain.consent.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consents", schema = "public")
public class ConsentEntity {

	@Id
	@GeneratedValue
	@UuidGenerator
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "guest_session_id")
	private UUID guestSessionId;

	@Column(
		name = "document_type",
		nullable = false,
		columnDefinition = "public.consent_document_type"
	)
	@ColumnTransformer(write = "cast(? as public.consent_document_type)")
	private String documentType;

	@Column(name = "document_version", nullable = false)
	private String documentVersion;

	@Column(name = "accepted_at", nullable = false)
	private Instant acceptedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	@ColumnDefault("CURRENT_TIMESTAMP")
	private Instant createdAt;

	protected ConsentEntity() {
	}

	private ConsentEntity(
		UUID userId,
		UUID guestSessionId,
		ConsentDocumentType documentType,
		String documentVersion,
		Instant acceptedAt
	) {
		this.userId = userId;
		this.guestSessionId = guestSessionId;
		this.documentType = documentType.value();
		this.documentVersion = documentVersion;
		this.acceptedAt = acceptedAt;
	}

	public static ConsentEntity forGuest(
		UUID guestSessionId,
		ConsentDocumentType documentType,
		String documentVersion,
		Instant acceptedAt
	) {
		return new ConsentEntity(
			null,
			guestSessionId,
			documentType,
			documentVersion,
			acceptedAt
		);
	}

	public static ConsentEntity forUser(
		UUID userId,
		ConsentDocumentType documentType,
		String documentVersion,
		Instant acceptedAt
	) {
		return new ConsentEntity(
			userId,
			null,
			documentType,
			documentVersion,
			acceptedAt
		);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getGuestSessionId() {
		return guestSessionId;
	}

	public ConsentDocumentType getDocumentType() {
		return ConsentDocumentType.fromValue(documentType);
	}

	public String getDocumentVersion() {
		return documentVersion;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
