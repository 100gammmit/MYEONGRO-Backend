package com.myeongro.api.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AccountWithdrawalMigrationContractTests {

	private static final Path MIGRATION_DIRECTORY =
		Path.of("src", "main", "resources", "db", "migration");

	@Test
	void v5AddsProfileSoftDeletionSupport() throws IOException {
		var migration = Files.list(MIGRATION_DIRECTORY)
			.filter(path -> path.getFileName().toString()
				.startsWith("V5__account_withdrawal"))
			.findFirst()
			.orElseThrow();

		String sql = Files.readString(migration);

		assertThat(sql)
			.contains("alter table public.profiles")
			.contains("add column if not exists deleted_at timestamptz")
			.contains("profiles_active_idx")
			.contains("where deleted_at is null");
	}

	@Test
	void v6AddsAccountRetentionPurgeColumns() throws IOException {
		var migration = Files.list(MIGRATION_DIRECTORY)
			.filter(path -> path.getFileName().toString()
				.startsWith("V6__account_retention_purge_policy"))
			.findFirst()
			.orElseThrow();

		String sql = Files.readString(migration);

		assertThat(sql)
			.contains("add column if not exists purge_after timestamptz")
			.contains("add column if not exists purged_at timestamptz")
			.contains("profiles_purge_due_idx")
			.contains("purge_after")
			.contains("purged_at is null");
	}
}
