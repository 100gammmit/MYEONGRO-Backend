package com.myeongro.api.domain.profile.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.myeongro.api.domain.profile.entity.OAuthAccountEntity;

public interface OAuthAccountJpaRepository extends JpaRepository<OAuthAccountEntity, Long> {

	@Query("""
		select account
		from OAuthAccountEntity account
		join fetch account.profile profile
		where account.provider = :provider
		  and account.providerUserId = :providerUserId
		  and profile.deletedAt is null
		""")
	Optional<OAuthAccountEntity> findActiveByProviderAccount(
		@Param("provider") String provider,
		@Param("providerUserId") String providerUserId
	);
}
