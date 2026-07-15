package com.myeongro.api.domain.consent.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.myeongro.api.domain.consent.entity.ConsentEntity;

public interface ConsentRepository extends JpaRepository<ConsentEntity, Long> {

	Optional<ConsentEntity> findByUserId(UUID userId);
}
