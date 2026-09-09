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

		Flyway migrationThroughV10 = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.locations("classpath:db/migration")
			.defaultSchema("public")
			.schemas("public", "auth")
			.cleanDisabled(false)
			.target("10")
			.load();

		migrationThroughV10.clean();
		var result = migrationThroughV10.migrate();

		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(10);

		UUID withdrawnUserId = seedWithdrawnAccount(jdbcUrl, username, password);
		UUID activeUserId = seedActiveAccount(jdbcUrl, username, password);
		Flyway latest = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.locations("classpath:db/migration")
			.defaultSchema("public")
			.schemas("public", "auth")
			.cleanDisabled(false)
			.load();
		var latestResult = latest.migrate();

		assertThat(latestResult.success).isTrue();
		assertThat(latestResult.migrationsExecuted).isEqualTo(2);
		verifyImmediateDeletionMigration(
			jdbcUrl, username, password, withdrawnUserId, activeUserId
		);

		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.createStatement();
			 var resultSet = statement.executeQuery("""
				 select count(*)
				 from public.flyway_schema_history
				 where success = true
				   and version is not null
				 """)) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getInt(1)).isGreaterThanOrEqualTo(12);
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
				   ) is not null,
				   not exists (
				     select 1 from information_schema.columns
				     where table_schema = 'public' and table_name = 'profiles'
				       and column_name in ('deleted_at', 'purge_after', 'purged_at')
				   ),
				   to_regclass('public.adult_eligibility_assertions') is not null
				 """)) {
			assertThat(resultSet.next()).isTrue();
			for (int column = 1; column <= 11; column++) {
				assertThat(resultSet.getBoolean(column)).isTrue();
			}
		}

		verifyFailureFunctionPrivileges(jdbcUrl, username, password);
		verifyReadingCreationContract(jdbcUrl, username, password);
		verifyConsentTransitionSerialization(jdbcUrl, username, password);
	}

	private UUID seedWithdrawnAccount(
		String jdbcUrl,
		String username,
		String password
	) throws SQLException {
		UUID userId = UUID.randomUUID();
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
			try (var profile = connection.prepareStatement("""
				insert into public.profiles (id, display_name, deleted_at, purge_after)
				values (?, 'withdrawn', current_timestamp, current_timestamp + interval '30 days')
				""")) {
				profile.setObject(1, userId);
				profile.executeUpdate();
			}
			try (var account = connection.prepareStatement("""
				insert into public.oauth_accounts (profile_id, provider, provider_user_id)
				values (?, 'google', ?)
				""")) {
				account.setObject(1, userId);
				account.setString(2, "withdrawn-" + userId);
				account.executeUpdate();
			}
			try (var consent = connection.prepareStatement("""
				insert into public.consent_events (
				  user_id, document_type, document_version, action, occurred_at, method
				) values (?, 'TERMS', 'legacy-test', 'ACCEPTED', current_timestamp, 'test')
				""")) {
				consent.setObject(1, userId);
				consent.executeUpdate();
			}
		}
		return userId;
	}

	private UUID seedActiveAccount(
		String jdbcUrl,
		String username,
		String password
	) throws SQLException {
		UUID userId = UUID.randomUUID();
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var profile = connection.prepareStatement(
				 "insert into public.profiles (id, display_name) values (?, 'active')")) {
			profile.setObject(1, userId);
			profile.executeUpdate();
		}
		return userId;
	}

	private void verifyImmediateDeletionMigration(
		String jdbcUrl,
		String username,
		String password,
		UUID withdrawnUserId,
		UUID activeUserId
	) throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
			assertProfileCount(connection, withdrawnUserId, 0);
			assertProfileCount(connection, activeUserId, 1);
			try (var account = connection.prepareStatement(
				"select count(*) from public.oauth_accounts where profile_id = ?")) {
				account.setObject(1, withdrawnUserId);
				try (var resultSet = account.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
					assertThat(resultSet.getInt(1)).isZero();
				}
			}
			try (var consent = connection.prepareStatement(
				"select count(*) from public.consent_events where user_id = ?")) {
				consent.setObject(1, withdrawnUserId);
				try (var resultSet = consent.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
					assertThat(resultSet.getInt(1)).isZero();
				}
			}
		}
	}

	private void assertProfileCount(Connection connection, UUID userId, int expected)
		throws SQLException {
		try (var profile = connection.prepareStatement(
			"select count(*) from public.profiles where id = ?")) {
			profile.setObject(1, userId);
			try (var resultSet = profile.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getInt(1)).isEqualTo(expected);
			}
		}
	}

	private void verifyConsentTransitionSerialization(
		String jdbcUrl,
		String username,
		String password
	) throws Exception {
		UUID userId = UUID.randomUUID();
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var profile = connection.prepareStatement(
				 "insert into public.profiles (id) values (?)");
			 var withdrawn = connection.prepareStatement("""
				 insert into public.consent_events (
				   user_id, document_type, document_version, action, occurred_at, method
				 ) values (?, 'AI_OVERSEAS_TRANSFER', '2026-09-20', 'WITHDRAWN',
				   clock_timestamp() - interval '1 minute', 'test')
				 """)) {
			profile.setObject(1, userId);
			profile.executeUpdate();
			withdrawn.setObject(1, userId);
			withdrawn.executeUpdate();
		}

		var executor = Executors.newSingleThreadExecutor();
		try (var acceptance = DriverManager.getConnection(jdbcUrl, username, password)) {
			acceptance.setAutoCommit(false);
			lockConsentTransition(acceptance, userId);
			assertThat(latestConsentAction(acceptance, userId)).isEqualTo("WITHDRAWN");

			var withdrawal = executor.submit(() -> {
				try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
					connection.setAutoCommit(false);
					lockConsentTransition(connection, userId);
					String observedAction = latestConsentAction(connection, userId);
					insertConsentEvent(connection, userId, "WITHDRAWN");
					connection.commit();
					return observedAction;
				}
			});
			awaitAdvisoryWaiter(jdbcUrl, username, password);

			insertConsentEvent(acceptance, userId, "ACCEPTED");
			acceptance.commit();

			assertThat(withdrawal.get(5, TimeUnit.SECONDS)).isEqualTo("ACCEPTED");
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}

		try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
			assertThat(latestConsentAction(connection, userId)).isEqualTo("WITHDRAWN");
		}
	}

	private void lockConsentTransition(Connection connection, UUID userId) throws SQLException {
		try (var statement = connection.prepareStatement("""
			select pg_advisory_xact_lock(
			  hashtextextended(
			    'consent:' || cast(? as text) || ':AI_OVERSEAS_TRANSFER', 0
			  )
			)
			""")) {
			statement.setObject(1, userId);
			statement.execute();
		}
	}

	private String latestConsentAction(Connection connection, UUID userId) throws SQLException {
		try (var statement = connection.prepareStatement("""
			select action
			from public.consent_events
			where user_id = ? and document_type = 'AI_OVERSEAS_TRANSFER'
			order by occurred_at desc, id desc
			limit 1
			""")) {
			statement.setObject(1, userId);
			try (var resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getString("action");
			}
		}
	}

	private void insertConsentEvent(
		Connection connection,
		UUID userId,
		String action
	) throws SQLException {
		try (var statement = connection.prepareStatement("""
			insert into public.consent_events (
			  user_id, document_type, document_version, action, occurred_at, method
			) values (?, 'AI_OVERSEAS_TRANSFER', '2026-09-20', ?, clock_timestamp(), 'test')
			""")) {
			statement.setObject(1, userId);
			statement.setString(2, action);
			statement.executeUpdate();
		}
	}

	private void verifyFailureFunctionPrivileges(String jdbcUrl, String username, String password)
		throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.createStatement();
			 var resultSet = statement.executeQuery("""
				 select
				   has_function_privilege(
				     'anon', 'public.fail_reading_generation(uuid,bigint,text)', 'EXECUTE'
				   ),
				   has_function_privilege(
				     'authenticated', 'public.fail_reading_generation(uuid,bigint,text)', 'EXECUTE'
				   ),
				   has_function_privilege(
				     'service_role', 'public.fail_reading_generation(uuid,bigint,text)', 'EXECUTE'
				   )
				 """)) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getBoolean(1)).isFalse();
			assertThat(resultSet.getBoolean(2)).isFalse();
			assertThat(resultSet.getBoolean(3)).isTrue();
		}
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
		verifyCompletionAndReplayDoNotDeadlock(
			jdbcUrl, username, password, userId, completedRequestId, completed
		);
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

	private void verifyCompletionAndReplayDoNotDeadlock(
		String jdbcUrl,
		String username,
		String password,
		UUID userId,
		UUID requestId,
		PendingReading pending
	) throws Exception {
		var executor = Executors.newFixedThreadPool(2);
		try (var blocker = DriverManager.getConnection(jdbcUrl, username, password)) {
			blocker.setAutoCommit(false);
			try (var lock = blocker.prepareStatement("""
				 select id from public.readings where id = ? for update
				 """)) {
				lock.setObject(1, pending.readingId());
				try (var resultSet = lock.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
				}
			}

			var completion = executor.submit(() -> {
				complete(jdbcUrl, username, password, pending);
				return "completed";
			});
			awaitUserAdvisoryLock(jdbcUrl, username, password, userId);

			var replay = executor.submit(() -> createPending(
				jdbcUrl, username, password, userId, requestId, "hash-a"
			));
			awaitAdvisoryWaiter(jdbcUrl, username, password);

			blocker.commit();
			assertThat(completion.get(5, TimeUnit.SECONDS)).isEqualTo("completed");
			assertThat(replay.get(5, TimeUnit.SECONDS)).isEqualTo(
				new PendingReading(pending.readingId(), pending.generationId(), false)
			);
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}
	}

	private void awaitUserAdvisoryLock(
		String jdbcUrl,
		String username,
		String password,
		UUID userId
	) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
				 var statement = connection.prepareStatement("""
					 select pg_try_advisory_lock(
					   hashtextextended('reading-credit:' || ?::text, 0)
					 )
					 """)) {
				statement.setObject(1, userId);
				try (var resultSet = statement.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
					if (!resultSet.getBoolean(1)) {
						return;
					}
				}
				try (var unlock = connection.prepareStatement("""
					 select pg_advisory_unlock(
					   hashtextextended('reading-credit:' || ?::text, 0)
					 )
					 """)) {
					unlock.setObject(1, userId);
					unlock.execute();
				}
			}
			Thread.sleep(25);
		}
		throw new AssertionError("Completion did not acquire the user advisory lock first");
	}

	private void awaitAdvisoryWaiter(
		String jdbcUrl,
		String username,
		String password
	) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
				 var statement = connection.prepareStatement("""
					 select exists (
					   select 1 from pg_locks
					   where locktype = 'advisory' and not granted
					 )
					 """)) {
				try (var resultSet = statement.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
					if (resultSet.getBoolean(1)) {
						return;
					}
				}
			}
			Thread.sleep(25);
		}
		throw new AssertionError("Replay did not wait for the user advisory lock");
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
			statement.setString(5, "mind_three_card");
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
