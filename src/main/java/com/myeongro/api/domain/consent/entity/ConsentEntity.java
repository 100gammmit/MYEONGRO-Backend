package com.myeongro.api.domain.consent.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "consents", schema = "public")
public class ConsentEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "terms_version", nullable = false)
	private String termsVersion;

	@Column(name = "privacy_version", nullable = false)
	private String privacyVersion;

	@Column(name = "sensitive_data_version", nullable = false)
	private String sensitiveDataVersion;

	@Column(name = "terms_accepted_at", nullable = false)
	private Instant termsAcceptedAt;

	@Column(name = "privacy_accepted_at", nullable = false)
	private Instant privacyAcceptedAt;

	@Column(name = "sensitive_data_accepted_at", nullable = false)
	private Instant sensitiveDataAcceptedAt;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	@ColumnDefault("CURRENT_TIMESTAMP")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	@ColumnDefault("CURRENT_TIMESTAMP")
	private Instant updatedAt;

	protected ConsentEntity() {
	}

	private ConsentEntity(
		UUID userId,
		String termsVersion,
		String privacyVersion,
		String sensitiveDataVersion,
		Instant acceptedAt
	) {
		this.userId = userId;
		accept(termsVersion, privacyVersion, sensitiveDataVersion, acceptedAt);
	}

	public static ConsentEntity acceptedForUser(
		UUID userId,
		String termsVersion,
		String privacyVersion,
		String sensitiveDataVersion,
		Instant acceptedAt
	) {
		return new ConsentEntity(
			userId,
			termsVersion,
			privacyVersion,
			sensitiveDataVersion,
			acceptedAt
		);
	}

	public void accept(
		String termsVersion,
		String privacyVersion,
		String sensitiveDataVersion,
		Instant acceptedAt
	) {
		this.termsVersion = termsVersion;
		this.privacyVersion = privacyVersion;
		this.sensitiveDataVersion = sensitiveDataVersion;
		this.termsAcceptedAt = acceptedAt;
		this.privacyAcceptedAt = acceptedAt;
		this.sensitiveDataAcceptedAt = acceptedAt;
	}

	public boolean hasAcceptedCurrentVersions(
		String termsVersion,
		String privacyVersion,
		String sensitiveDataVersion
	) {
		return termsVersion.equals(this.termsVersion)
			&& privacyVersion.equals(this.privacyVersion)
			&& sensitiveDataVersion.equals(this.sensitiveDataVersion);
	}

	public void acceptOutdatedVersions(
		String termsVersion,
		String privacyVersion,
		String sensitiveDataVersion,
		Instant acceptedAt
	) {
		if (!termsVersion.equals(this.termsVersion)) {
			this.termsVersion = termsVersion;
			this.termsAcceptedAt = acceptedAt;
		}
		if (!privacyVersion.equals(this.privacyVersion)) {
			this.privacyVersion = privacyVersion;
			this.privacyAcceptedAt = acceptedAt;
		}
		if (!sensitiveDataVersion.equals(this.sensitiveDataVersion)) {
			this.sensitiveDataVersion = sensitiveDataVersion;
			this.sensitiveDataAcceptedAt = acceptedAt;
		}
	}

	public Long getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String versionOf(ConsentDocumentType documentType) {
		return switch (documentType) {
			case TERMS -> termsVersion;
			case PRIVACY -> privacyVersion;
			case SENSITIVE_DATA -> sensitiveDataVersion;
		};
	}

	public Instant acceptedAtOf(ConsentDocumentType documentType) {
		return switch (documentType) {
			case TERMS -> termsAcceptedAt;
			case PRIVACY -> privacyAcceptedAt;
			case SENSITIVE_DATA -> sensitiveDataAcceptedAt;
		};
	}

	public String getTermsVersion() {
		return termsVersion;
	}

	public String getPrivacyVersion() {
		return privacyVersion;
	}

	public String getSensitiveDataVersion() {
		return sensitiveDataVersion;
	}

	public Instant getTermsAcceptedAt() {
		return termsAcceptedAt;
	}

	public Instant getPrivacyAcceptedAt() {
		return privacyAcceptedAt;
	}

	public Instant getSensitiveDataAcceptedAt() {
		return sensitiveDataAcceptedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
