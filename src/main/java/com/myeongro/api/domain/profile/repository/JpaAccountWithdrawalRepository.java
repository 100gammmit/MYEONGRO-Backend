package com.myeongro.api.domain.profile.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class JpaAccountWithdrawalRepository implements AccountWithdrawalRepository {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	@Transactional
	public void withdraw(UUID userId, Instant purgeAfter) {
		softDeleteReadings(userId);
		deleteOAuthAccounts(userId);
		softDeleteProfile(userId, purgeAfter);
	}

	private void softDeleteReadings(UUID userId) {
		entityManager.createNativeQuery("""
			update public.readings
			set deleted_at = current_timestamp
			where user_id = :userId
			  and deleted_at is null
			""")
			.setParameter("userId", userId)
			.executeUpdate();
	}

	private void deleteOAuthAccounts(UUID userId) {
		entityManager.createNativeQuery("""
			delete from public.oauth_accounts
			where profile_id = :userId
			""")
			.setParameter("userId", userId)
			.executeUpdate();
	}

	private void softDeleteProfile(UUID userId, Instant purgeAfter) {
		entityManager.createNativeQuery("""
			update public.profiles
			set deleted_at = coalesce(deleted_at, current_timestamp),
				purge_after = coalesce(purge_after, :purgeAfter),
				updated_at = current_timestamp
			where id = :userId
			""")
			.setParameter("purgeAfter", purgeAfter)
			.setParameter("userId", userId)
			.executeUpdate();
	}
}
