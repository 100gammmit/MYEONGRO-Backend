package com.myeongro.api.domain.profile.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.domain.profile.repository.AccountWithdrawalRepository;

@Service
public class AccountWithdrawalService {

	private final AccountWithdrawalRepository repository;
	private final AccountSessionRevoker sessionRevoker;

	public AccountWithdrawalService(
		AccountWithdrawalRepository repository,
		AccountSessionRevoker sessionRevoker
	) {
		this.repository = repository;
		this.sessionRevoker = sessionRevoker;
	}

	@Transactional
	public void withdraw(UUID userId) {
		repository.deletePermanently(userId);
		sessionRevoker.revokeAll(userId);
	}
}
