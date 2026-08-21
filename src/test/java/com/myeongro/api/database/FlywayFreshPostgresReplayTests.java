package com.myeongro.api.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
				     'public.create_pending_reading(uuid,uuid,text,public.reading_kind,text,integer,jsonb,text,text,text,integer,integer)'
				   ) is not null,
				   to_regprocedure(
				     'public.complete_reading_generation(uuid,bigint,text,jsonb,integer)'
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

		verifyReadingCreationContract(jdbcUrl, username, password);
	}

	private void verifyReadingCreationContract(String jdbcUrl, String username, String password) throws Exception {
		UUID userId = UUID.randomUUID();
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement("insert into public.profiles (id) values (?)")) {
			statement.setObject(1, userId);
			statement.executeUpdate();
		}

		UUID completedRequestId = UUID.randomUUID();
		PendingReading completed = createPending(jdbcUrl, username, password, userId, completedRequestId, "hash-a");
		complete(jdbcUrl, username, password, completed);
		assertBalances(jdbcUrl, username, password, userId, 9, 0);

		PendingReading reused = createPending(jdbcUrl, username, password, userId, completedRequestId, "hash-a");
		assertThat(reused).isEqualTo(new PendingReading(completed.readingId(), completed.generationId(), false));
		assertSqlState("RL104", () ->
			createPending(jdbcUrl, username, password, userId, completedRequestId, "hash-b"));

		UUID failedRequestId = UUID.randomUUID();
		PendingReading failed = createPending(jdbcUrl, username, password, userId, failedRequestId, "hash-c");
		assertSqlState("RL110", () ->
			createPending(jdbcUrl, username, password, userId, failedRequestId, "hash-c"));
		fail(jdbcUrl, username, password, failed);
		assertBalances(jdbcUrl, username, password, userId, 9, 0);
		assertSqlState("RL110", () ->
			createPending(jdbcUrl, username, password, userId, failedRequestId, "hash-c"));

		PendingReading independent = createPending(
			jdbcUrl, username, password, userId, UUID.randomUUID(), "hash-independent");
		assertThat(independent.created()).isTrue();
		assertThat(independent.readingId()).isNotEqualTo(completed.readingId());
		assertSqlState("RL111", () -> createPending(
			jdbcUrl, username, password, userId, UUID.randomUUID(), "hash-active"));
		fail(jdbcUrl, username, password, independent);

		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement("""
				 update public.profiles set paid_credit_balance = 2 where id = ?
				 """)) {
			statement.setObject(1, userId);
			statement.executeUpdate();
		}
		PendingReading paid = createPending(
			jdbcUrl, username, password, userId, UUID.randomUUID(), "hash-paid", 10);
		complete(jdbcUrl, username, password, paid);
		assertBalances(jdbcUrl, username, password, userId, 0, 1);
		assertSqlState("RL112", () -> createPending(
			jdbcUrl, username, password, userId, UUID.randomUUID(),
			"hash-insufficient", 2));

		UUID concurrentUserId = UUID.randomUUID();
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement("insert into public.profiles (id) values (?)")) {
			statement.setObject(1, concurrentUserId);
			statement.executeUpdate();
		}
		verifyConcurrentReservation(jdbcUrl, username, password, concurrentUserId);
	}

	private void verifyConcurrentReservation(
		String jdbcUrl,
		String username,
		String password,
		UUID userId
	) throws Exception {
		UUID requestId = UUID.randomUUID();
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> reservationOutcome(
				start, jdbcUrl, username, password, userId, requestId));
			var second = executor.submit(() -> reservationOutcome(
				start, jdbcUrl, username, password, userId, requestId));
			start.countDown();

			assertThat(List.of(first.get(), second.get()))
				.containsExactlyInAnyOrder("created", "RL110");
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}

		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement("""
				 select count(*) from public.readings where user_id = ? and request_id = ?
				 """)) {
			statement.setObject(1, userId);
			statement.setObject(2, requestId);
			try (var resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getInt(1)).isEqualTo(1);
			}
		}
		verifyStaleCleanup(jdbcUrl, username, password, userId, requestId);
	}

	private String reservationOutcome(
		CountDownLatch start,
		String jdbcUrl,
		String username,
		String password,
		UUID userId,
		UUID requestId
	) throws Exception {
		start.await();
		try {
			PendingReading pending = createPending(
				jdbcUrl, username, password, userId, requestId, "hash-concurrent");
			return pending.created() ? "created" : "reused";
		} catch (SQLException exception) {
			return exception.getSQLState();
		}
	}

	private PendingReading createPending(
		String jdbcUrl,
		String username,
		String password,
		UUID userId,
		UUID requestId,
		String inputHash
	) throws SQLException {
		return createPending(jdbcUrl, username, password, userId, requestId, inputHash, 1);
	}

	private PendingReading createPending(
		String jdbcUrl,
		String username,
		String password,
		UUID userId,
		UUID requestId,
		String inputHash,
		int creditCost
	) throws SQLException {
		try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement("""
				 select reading_id, generation_id, created
				 from public.create_pending_reading(
				   ?, ?, ?, cast(? as public.reading_kind), ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?
				 )
				 """)) {
			statement.setObject(1, userId);
			statement.setObject(2, requestId);
			statement.setString(3, inputHash);
			statement.setString(4, "tarot");
			statement.setString(5, "daily_one_card");
			statement.setInt(6, 1);
			statement.setString(7, "{}");
			statement.setString(8, "openai");
			statement.setString(9, "test-model");
			statement.setString(10, "test-prompt");
			statement.setInt(11, creditCost);
			statement.setInt(12, 10);
			try (var resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return new PendingReading(
					resultSet.getObject("reading_id", UUID.class),
					resultSet.getLong("generation_id"),
					resultSet.getBoolean("created")
				);
			}
		}
	}

	private void complete(String jdbcUrl, String username, String password, PendingReading pending)
		throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement("""
				 select public.complete_reading_generation(?, ?, ?, cast(? as jsonb), ?)
				 """)) {
			statement.setObject(1, pending.readingId());
			statement.setLong(2, pending.generationId());
			statement.setString(3, "Completed reading");
			statement.setString(4, "{}");
			statement.setInt(5, 10);
			statement.execute();
		}
	}

	private void assertBalances(
		String jdbcUrl,
		String username,
		String password,
		UUID userId,
		int expectedFree,
		int expectedPaid
	) throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement("""
				 select free_credit_balance, paid_credit_balance
				 from public.profiles where id = ?
				 """)) {
			statement.setObject(1, userId);
			try (var resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getInt(1)).isEqualTo(expectedFree);
				assertThat(resultSet.getInt(2)).isEqualTo(expectedPaid);
			}
		}
	}

	private void verifyStaleCleanup(
		String jdbcUrl,
		String username,
		String password,
		UUID userId,
		UUID requestId
	) throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
			try (var age = connection.prepareStatement("""
				 update public.generation_records gr
				 set created_at = current_timestamp - interval '10 minutes'
				 from public.readings r
				 where r.id = gr.reading_id and r.user_id = ? and r.request_id = ?
				 """)) {
				age.setObject(1, userId);
				age.setObject(2, requestId);
				assertThat(age.executeUpdate()).isEqualTo(1);
			}
			try (var cleanup = connection.prepareStatement("""
				 select public.fail_stale_reading_generations(interval '5 minutes')
				 """)) {
				try (var resultSet = cleanup.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
					assertThat(resultSet.getInt(1)).isEqualTo(1);
				}
			}
			try (var status = connection.prepareStatement("""
				 select r.status::text, gr.status::text, gr.error_code
				 from public.readings r
				 join public.generation_records gr on gr.reading_id = r.id
				 where r.user_id = ? and r.request_id = ?
				 """)) {
				status.setObject(1, userId);
				status.setObject(2, requestId);
				try (var resultSet = status.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
					assertThat(resultSet.getString(1)).isEqualTo("failed");
					assertThat(resultSet.getString(2)).isEqualTo("failed");
					assertThat(resultSet.getString(3)).isEqualTo("GENERATION_TIMEOUT");
				}
			}
		}
	}

	private void fail(String jdbcUrl, String username, String password, PendingReading pending)
		throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement("select public.fail_reading_generation(?, ?, ?)")) {
			statement.setObject(1, pending.readingId());
			statement.setLong(2, pending.generationId());
			statement.setString(3, "TEST_FAILURE");
			statement.execute();
		}
	}

	private void assertSqlState(String expected, SqlOperation operation) {
		try {
			operation.run();
		} catch (SQLException exception) {
			assertThat(exception.getSQLState()).isEqualTo(expected);
			return;
		}
		throw new AssertionError("Expected SQL state " + expected);
	}

	private record PendingReading(UUID readingId, long generationId, boolean created) {
	}

	@FunctionalInterface
	private interface SqlOperation {
		void run() throws SQLException;
	}
}
