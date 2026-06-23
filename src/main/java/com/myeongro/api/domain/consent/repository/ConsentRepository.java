package com.myeongro.api.domain.consent.repository;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.myeongro.api.domain.consent.entity.ConsentEntity;

public interface ConsentRepository extends JpaRepository<ConsentEntity, UUID> {

	List<ConsentEntity> findAllByGuestSessionId(UUID guestSessionId);

	List<ConsentEntity> findAllByUserId(UUID userId);

	@Modifying
	@Query(value = """
		insert into public.consents (
			user_id,
			guest_session_id,
			document_type,
			document_version,
			accepted_at
		)
		values (
			null,
			:guestSessionId,
			cast(:documentType as public.consent_document_type),
			:documentVersion,
			:acceptedAt
		)
		on conflict (guest_session_id, document_type, document_version)
			where guest_session_id is not null
		do nothing
		""", nativeQuery = true)
	int insertGuestConsentIfAbsent(
		@Param("guestSessionId") UUID guestSessionId,
		@Param("documentType") String documentType,
		@Param("documentVersion") String documentVersion,
		@Param("acceptedAt") Instant acceptedAt
	);

	@Modifying
	@Query(value = """
		insert into public.consents (
			user_id,
			guest_session_id,
			document_type,
			document_version,
			accepted_at
		)
		values (
			:userId,
			null,
			cast(:documentType as public.consent_document_type),
			:documentVersion,
			:acceptedAt
		)
		on conflict (user_id, document_type, document_version)
			where user_id is not null
		do nothing
		""", nativeQuery = true)
	int insertUserConsentIfAbsent(
		@Param("userId") UUID userId,
		@Param("documentType") String documentType,
		@Param("documentVersion") String documentVersion,
		@Param("acceptedAt") Instant acceptedAt
	);
}
