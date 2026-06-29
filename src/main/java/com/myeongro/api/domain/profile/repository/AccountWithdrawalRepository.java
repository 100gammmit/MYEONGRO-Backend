package com.myeongro.api.domain.profile.repository;

import java.util.UUID;

public interface AccountWithdrawalRepository {

	void withdraw(UUID userId);
}
