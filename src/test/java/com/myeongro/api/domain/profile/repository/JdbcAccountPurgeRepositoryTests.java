package com.myeongro.api.domain.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcAccountPurgeRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

	private final JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
	private final JdbcAccountPurgeRepository repository =
		new JdbcAccountPurgeRepository(jdbcTemplate);

	@Test
	void purgesDueReadingsAndAnonymizesProfilesWithoutDeletingProfiles() {
		when(jdbcTemplate.update(org.mockito.Mockito.any(String.class), org.mockito.Mockito.any(Object[].class)))
			.thenReturn(0, 0, 3);

		int purged = repository.purgeDueProfiles(NOW, 100);

		assertThat(purged).isEqualTo(3);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate, org.mockito.Mockito.times(3))
			.update(sql.capture(), org.mockito.Mockito.any(Object[].class));

		assertThat(sql.getAllValues().get(0))
			.contains("delete from public.readings")
			.contains("not exists")
			.contains("from public.purchases")
			.contains("purchases.reading_id = readings.id")
			.contains("purged_at is null");
		assertThat(sql.getAllValues().get(1))
			.contains("delete from public.oauth_accounts")
			.contains("purged_at is null");
		assertThat(sql.getAllValues().get(2))
			.contains("update public.profiles")
			.contains("display_name = null")
			.contains("purged_at = coalesce(purged_at, ?)")
			.doesNotContain("delete from public.profiles");
	}
}
