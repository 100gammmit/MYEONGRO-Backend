package com.myeongro.api.domain.profile.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "oauth_accounts",
	schema = "public",
	uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"})
)
public class OAuthAccountEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "profile_id", nullable = false)
	private ProfileEntity profile;

	@Column(nullable = false)
	private String provider;

	@Column(name = "provider_user_id", nullable = false)
	private String providerUserId;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	protected OAuthAccountEntity() {
	}

	private OAuthAccountEntity(
		ProfileEntity profile,
		String provider,
		String providerUserId
	) {
		this.profile = profile;
		this.provider = provider;
		this.providerUserId = providerUserId;
	}

	public static OAuthAccountEntity create(
		ProfileEntity profile,
		String provider,
		String providerUserId
	) {
		return new OAuthAccountEntity(profile, provider, providerUserId);
	}

	public Long getId() {
		return id;
	}

	public ProfileEntity getProfile() {
		return profile;
	}

	public String getProvider() {
		return provider;
	}

	public String getProviderUserId() {
		return providerUserId;
	}

}
