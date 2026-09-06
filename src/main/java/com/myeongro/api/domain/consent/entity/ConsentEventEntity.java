package com.myeongro.api.domain.consent.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consent_events", schema = "public")
public class ConsentEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "document_type", nullable = false)
	private ConsentDocumentType documentType;

	@Column(name = "document_version", nullable = false)
	private String documentVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false)
	private ConsentAction action;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "method", nullable = false)
	private String method;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	@ColumnDefault("CURRENT_TIMESTAMP")
	private Instant createdAt;

	protected ConsentEventEntity() {
	}

	private ConsentEventEntity(
		UUID userId,
		ConsentDocumentType documentType,
		String documentVersion,
		ConsentAction action,
		Instant occurredAt,
		String method
	) {
		this.userId = userId;
		this.documentType = documentType;
		this.documentVersion = documentVersion;
		this.action = action;
		this.occurredAt = occurredAt;
		this.method = method;
	}

	public static ConsentEventEntity accepted(
		UUID userId,
		ConsentDocumentType documentType,
		String documentVersion,
		Instant occurredAt
	) {
		return new ConsentEventEntity(
			userId,
			documentType,
			documentVersion,
			ConsentAction.ACCEPTED,
			occurredAt,
			"document-modal"
		);
	}

	public static ConsentEventEntity withdrawn(
		UUID userId,
		ConsentDocumentType documentType,
		String documentVersion,
		Instant occurredAt
	) {
		return new ConsentEventEntity(
			userId,
			documentType,
			documentVersion,
			ConsentAction.WITHDRAWN,
			occurredAt,
			"account-settings"
		);
	}

	public Long getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public ConsentDocumentType getDocumentType() {
		return documentType;
	}

	public String getDocumentVersion() {
		return documentVersion;
	}

	public ConsentAction getAction() {
		return action;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getMethod() {
		return method;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
