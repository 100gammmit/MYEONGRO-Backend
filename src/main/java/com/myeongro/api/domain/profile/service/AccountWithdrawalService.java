package com.myeongro.api.domain.profile.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.domain.profile.repository.AccountWithdrawalRepository;

@Service
public class AccountWithdrawalService {

	private final AccountWithdrawalRepository repository;

	public AccountWithdrawalService(AccountWithdrawalRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public void withdraw(UUID userId) {
		repository.withdraw(userId);
	}
}
