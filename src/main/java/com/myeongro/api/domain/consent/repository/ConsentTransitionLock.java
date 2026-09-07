package com.myeongro.api.domain.consent.repository;

import java.util.UUID;

import com.myeongro.api.domain.consent.entity.ConsentDocumentType;

public interface ConsentTransitionLock {

	void lock(UUID userId, ConsentDocumentType documentType);
}
