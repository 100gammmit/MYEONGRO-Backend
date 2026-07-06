package com.myeongro.api.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.DriverManager;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayFreshPostgresReplayTests {

	@Test
	void replaysAllMigrationsOnEmptyPostgres() throws Exception {
		String jdbcUrl = System.getProperty("freshPostgresJdbcUrl");
		String username = System.getProperty("freshPostgresUsername");
		String password = System.getProperty("freshPostgresPassword");
		assumeTrue(jdbcUrl != null && username != null && password != null,
			"Set freshPostgresJdbcUrl, freshPostgresUsername, and freshPostgresPassword to run");

		Flyway flyway = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.locations("classpath:db/migration")
			.defaultSchema("public")
			.schemas("public", "auth")
			.cleanDisabled(false)
			.load();

		flyway.clean();
		var result = flyway.migrate();

		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isGreaterThan(0);

		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.createStatement();
			 var resultSet = statement.executeQuery("""
				 select count(*)
				 from public.flyway_schema_history
				 where success = true
				   and version is not null
				 """)) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getInt(1)).isGreaterThanOrEqualTo(6);
		}
	}
}
