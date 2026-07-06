package com.myeongro.api.domain.profile.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profiles", schema = "public")
public class ProfileEntity {

	@Id
	private UUID id;

	@Column(name = "display_name")
	private String displayName;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "purge_after")
	private Instant purgeAfter;

	@Column(name = "purged_at")
	private Instant purgedAt;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

	protected ProfileEntity() {
	}

	private ProfileEntity(UUID id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public static ProfileEntity create(UUID id, String displayName) {
		return new ProfileEntity(id, displayName);
	}

	public UUID getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public Instant getPurgeAfter() {
		return purgeAfter;
	}

	public Instant getPurgedAt() {
		return purgedAt;
	}
}
