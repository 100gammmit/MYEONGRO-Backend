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
			assertThat(resultSet.getInt(1)).isGreaterThanOrEqualTo(8);
		}

		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.createStatement();
			 var resultSet = statement.executeQuery("""
				 select
				   to_regclass('public.free_reading_quota_events') is null,
				   to_regclass('public.payment_webhook_events') is null,
				   to_regclass('public.purchases') is null,
				   to_regtype('public.reading_tier') is null,
				   to_regtype('public.purchase_status') is null,
				   not exists (
				     select 1
				     from information_schema.columns
				     where table_schema = 'public'
				       and table_name = 'readings'
				       and column_name = 'tier'
				   ),
				   to_regprocedure(
				     'public.create_pending_reading(uuid,uuid,text,public.reading_kind,text,integer,jsonb,text,text,text)'
				   ) is not null,
				   to_regprocedure(
				     'public.complete_reading_generation(uuid,bigint,text,jsonb)'
				   ) is not null,
				   to_regprocedure(
				     'public.fail_reading_generation(uuid,bigint,text)'
				   ) is not null
				 """)) {
			assertThat(resultSet.next()).isTrue();
			for (int column = 1; column <= 9; column++) {
				assertThat(resultSet.getBoolean(column)).isTrue();
			}
		}
	}
}
