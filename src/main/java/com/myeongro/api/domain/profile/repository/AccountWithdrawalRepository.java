package com.myeongro.api.domain.profile.repository;

import java.util.UUID;

public interface AccountWithdrawalRepository {

	void deletePermanently(UUID userId);
}
