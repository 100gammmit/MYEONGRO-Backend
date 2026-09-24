package com.myeongro.api.domain.profile.entity;

import java.time.Instant;
import java.time.LocalDate;
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

	@Column(name = "free_credit_balance", nullable = false)
	private int freeCreditBalance;

	@Column(name = "paid_credit_balance", nullable = false)
	private int paidCreditBalance;

	@Column(name = "free_credit_reset_date")
	private LocalDate freeCreditResetDate;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

	protected ProfileEntity() {
	}

	private ProfileEntity(UUID id) {
		this.id = id;
	}

	public static ProfileEntity create(UUID id) {
		return new ProfileEntity(id);
	}

	public UUID getId() {
		return id;
	}

}
