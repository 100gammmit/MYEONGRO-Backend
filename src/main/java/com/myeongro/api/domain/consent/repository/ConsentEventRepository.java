package com.myeongro.api.domain.consent.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.myeongro.api.domain.consent.entity.ConsentEventEntity;

public interface ConsentEventRepository extends JpaRepository<ConsentEventEntity, Long> {

	List<ConsentEventEntity> findAllByUserIdOrderByOccurredAtDescIdDesc(UUID userId);
}
