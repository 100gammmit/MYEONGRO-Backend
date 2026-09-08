package com.myeongro.api.domain.profile.repository;

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
	public void deletePermanently(UUID userId) {
		entityManager.createNativeQuery("""
			delete from public.profiles
			where id = :userId
			""")
			.setParameter("userId", userId)
			.executeUpdate();
	}
}
