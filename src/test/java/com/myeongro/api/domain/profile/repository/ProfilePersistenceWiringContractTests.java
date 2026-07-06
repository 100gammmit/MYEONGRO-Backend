package com.myeongro.api.domain.profile.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProfilePersistenceWiringContractTests {

	private static final Path REPOSITORY_PACKAGE = Path.of(
		"src",
		"main",
		"java",
		"com",
		"myeongro",
		"api",
		"domain",
		"profile",
		"repository"
	);

	@Test
	void oauthAndWithdrawalUseSingleJpaSpringAdapter() throws IOException {
		assertThat(REPOSITORY_PACKAGE.resolve("JdbcOAuthAccountRepository.java")).doesNotExist();
		assertThat(REPOSITORY_PACKAGE.resolve("JdbcAccountWithdrawalRepository.java")).doesNotExist();

		assertThat(Files.readString(REPOSITORY_PACKAGE.resolve("JpaOAuthAccountRepository.java")))
			.contains("@Repository")
			.doesNotContain("@Primary");
		assertThat(Files.readString(REPOSITORY_PACKAGE.resolve("JpaAccountWithdrawalRepository.java")))
			.contains("@Repository")
			.doesNotContain("@Primary");
	}

	@Test
	void accountPurgeKeepsJdbcAdapterForBatchSqlBoundary() {
		assertThat(REPOSITORY_PACKAGE.resolve("JdbcAccountPurgeRepository.java")).exists();
	}
}
