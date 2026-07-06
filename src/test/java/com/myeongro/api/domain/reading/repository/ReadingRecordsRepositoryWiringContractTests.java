package com.myeongro.api.domain.reading.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ReadingRecordsRepositoryWiringContractTests {

	@Test
	void onlyJpaReadingRecordsRepositoryRemainsAsTheRuntimeAdapter() throws IOException {
		Path repositoryPackage = Path.of(
			"src",
			"main",
			"java",
			"com",
			"myeongro",
			"api",
			"domain",
			"reading",
			"repository"
		);
		String jpaRepository = Files.readString(
			repositoryPackage.resolve("JpaReadingRecordsRepository.java")
		);

		assertThat(repositoryPackage.resolve("JdbcReadingRecordsRepository.java")).doesNotExist();
		assertThat(jpaRepository)
			.contains("@Repository")
			.doesNotContain("@Primary")
			.contains("JdbcTemplate")
			.contains("public.start_failed_reading_retry");
	}
}
