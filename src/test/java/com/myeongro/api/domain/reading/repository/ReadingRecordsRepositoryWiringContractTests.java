package com.myeongro.api.domain.reading.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ReadingRecordsRepositoryWiringContractTests {

	@Test
	void onlyJpaReadingRecordsRepositoryIsTheSpringBean() throws IOException {
		String jdbcRepository = Files.readString(Path.of(
			"src",
			"main",
			"java",
			"com",
			"myeongro",
			"api",
			"domain",
			"reading",
			"repository",
			"JdbcReadingRecordsRepository.java"
		));
		String jpaRepository = Files.readString(Path.of(
			"src",
			"main",
			"java",
			"com",
			"myeongro",
			"api",
			"domain",
			"reading",
			"repository",
			"JpaReadingRecordsRepository.java"
		));

		assertThat(jdbcRepository).doesNotContain("@Repository");
		assertThat(jpaRepository).contains("@Repository");
	}
}
