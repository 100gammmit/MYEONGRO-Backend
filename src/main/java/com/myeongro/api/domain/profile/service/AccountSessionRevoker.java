package com.myeongro.api.domain.profile.service;

import java.util.UUID;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

@Component
public class AccountSessionRevoker {

	private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

	public AccountSessionRevoker(
		FindByIndexNameSessionRepository<? extends Session> sessionRepository
	) {
		this.sessionRepository = sessionRepository;
	}

	public void revokeAll(UUID userId) {
		sessionRepository.findByPrincipalName(userId.toString())
			.keySet()
			.forEach(sessionRepository::deleteById);
	}
}
