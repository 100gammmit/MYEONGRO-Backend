package com.myeongro.api.domain.profile.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.myeongro.api.domain.profile.entity.ProfileEntity;

public interface ProfileJpaRepository extends JpaRepository<ProfileEntity, UUID> {
}
