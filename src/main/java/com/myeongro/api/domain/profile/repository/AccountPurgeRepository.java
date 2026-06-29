package com.myeongro.api.domain.profile.repository;

import java.time.Instant;

public interface AccountPurgeRepository {

	int purgeDueProfiles(Instant now, int batchSize);
}
