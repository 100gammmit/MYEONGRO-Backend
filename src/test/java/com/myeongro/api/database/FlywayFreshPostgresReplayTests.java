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
	void replaysConsolidatedV1OnEmptyPostgres() throws Exception {
		String jdbcUrl = System.getProperty("freshPostgresJdbcUrl");
		String username = System.getProperty("freshPostgresUsername");
		String password = System.getProperty("freshPostgresPassword");
		assumeTrue(jdbcUrl != null && username != null && password != null,
			"Set freshPostgresJdbcUrl, freshPostgresUsername, and freshPostgresPassword to run");

		Flyway flyway = Flyway.configure()
			.dataSource(jdbcUrl, username, password)
			.locations("classpath:db/migration")
			.defaultSchema("public")
			.schemas("public")
			.cleanDisabled(false)
			.load();

		flyway.clean();
		var baseline = Flyway.configure().dataSource(jdbcUrl, username, password)
			.locations("classpath:db/migration").target("2").load().migrate();
		UUID legacyOwner = createProfile(jdbcUrl, username, password);
		UUID legacyReading = UUID.randomUUID();
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			var insert = connection.prepareStatement("""
			 insert into public.readings(id, user_id, kind, title, input_payload, input_hash, schema_version)
			 values (?, ?, 'saju', 'legacy-v5', '{"targetYear":2026,"calculationSnapshot":{
			 "calculationVersion":"saju-ko-v4","engine":"lunar-java","engineVersion":"1.7.7","cityCatalogVersion":"kr-admin-v1",
			 "targetYear":2026,"pillars":{"year":null,"month":null,"day":null,"time":null},"fiveElements":{},
			 "relations":[],"limitations":[],"uncertainty":{"varyingFields":[]}}}'::jsonb, 'test-legacy', 5)
			 """)) {
			insert.setObject(1, legacyReading); insert.setObject(2, legacyOwner); insert.executeUpdate();
		}
		var result = flyway.migrate();

		assertThat(result.success).isTrue();
		assertThat(result.migrationsExecuted + baseline.migrationsExecuted).isEqualTo(4);
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			var query = connection.prepareStatement("select schema_version, jsonb_exists(input_payload->'calculationSnapshot', 'monthlyCalendar') from public.readings where id = ?")) {
			query.setObject(1, legacyReading);
			try (var row = query.executeQuery()) { assertThat(row.next()).isTrue(); assertThat(row.getInt(1)).isEqualTo(5); assertThat(row.getBoolean(2)).isFalse(); }
		}
		verifyHistory(jdbcUrl, username, password);
		verifyCurrentSchema(jdbcUrl, username, password);
		verifySecurityDefinerPrivileges(jdbcUrl, username, password);
		verifyReadingCreationContract(jdbcUrl, username, password);
		verifyAnnualReportContract(jdbcUrl, username, password);
		verifyConsentTransitionSerialization(jdbcUrl, username, password);
	}

	private void verifyHistory(String jdbcUrl, String username, String password)
		throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.createStatement();
			 var resultSet = statement.executeQuery("""
				 select version, description, success
				 from public.flyway_schema_history
				 where version is not null
				 order by installed_rank
				 """)) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("version")).isEqualTo("1");
			assertThat(resultSet.getString("description")).isEqualTo("initialize schema");
			assertThat(resultSet.getBoolean("success")).isTrue();
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("version")).isEqualTo("2");
			assertThat(resultSet.getBoolean("success")).isTrue();
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("version")).isEqualTo("3");
			assertThat(resultSet.getBoolean("success")).isTrue();
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getString("version")).isEqualTo("4");
			assertThat(resultSet.getBoolean("success")).isTrue();
			assertThat(resultSet.next()).isFalse();
		}
	}

	private void verifyCurrentSchema(String jdbcUrl, String username, String password)
		throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.createStatement();
			 var resultSet = statement.executeQuery("""
				 select
				   to_regclass('public.profiles') is not null,
				   to_regclass('public.oauth_accounts') is not null,
				   to_regclass('public.consent_events') is not null,
				   to_regclass('public.adult_eligibility_assertions') is not null,
				   to_regclass('public.readings') is not null,
				   to_regclass('public.generation_records') is not null,
				   to_regtype('public.reading_kind') is not null,
				   to_regtype('public.reading_status') is not null,
				   to_regtype('public.generation_status') is not null,
				   to_regprocedure(
				     'public.create_pending_reading(uuid,uuid,text,public.reading_kind,text,integer,jsonb,text,text,text,integer,integer,integer)'
				   ) is not null,
				   to_regprocedure(
				     'public.create_pending_reading(uuid,uuid,text,public.reading_kind,text,integer,jsonb,text,text,text,integer,integer)'
				   ) is not null,
				   to_regprocedure(
				     'public.complete_reading_generation(uuid,bigint,text,jsonb,integer)'
				   ) is not null,
				   to_regprocedure('public.fail_reading_generation(uuid,bigint,text)') is not null,
				   to_regprocedure('public.fail_stale_reading_generations(interval)') is not null,
				   to_regprocedure('public.get_reading_credit_status(uuid,integer)') is not null
				 """)) {
			assertThat(resultSet.next()).isTrue();
			for (int column = 1; column <= 15; column++) {
				assertThat(resultSet.getBoolean(column)).isTrue();
			}
		}
	}

	private void verifySecurityDefinerPrivileges(
		String jdbcUrl,
		String username,
		String password
	) throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.createStatement();
			 var resultSet = statement.executeQuery("""
				 select count(*)
				 from information_schema.routine_privileges
				 where routine_schema = 'public'
				   and grantee = 'PUBLIC'
				   and privilege_type = 'EXECUTE'
				   and routine_name in (
				     'apply_daily_reading_credit_reset',
				     'complete_reading_generation',
				     'create_pending_reading',
				     'fail_reading_generation',
				     'fail_stale_reading_generations',
				     'get_reading_credit_status'
				   )
				 """)) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getInt(1)).isZero();
		}
	}

	private void verifyReadingCreationContract(String jdbcUrl, String username, String password)
		throws Exception {
		UUID userId = createProfile(jdbcUrl, username, password);
		UUID completedRequestId = UUID.randomUUID();
		PendingReading completed = createPending(
			jdbcUrl, username, password, userId, completedRequestId, "hash-a", 1);
		assertThat(completed.created()).isTrue();
		assertSqlState("RL110", () -> createPending(
			jdbcUrl, username, password, userId, completedRequestId, "hash-a", 1));
		verifyCompletionAndReplayDoNotDeadlock(
			jdbcUrl, username, password, userId, completedRequestId, completed);
		PendingReading replayed = createPending(
			jdbcUrl, username, password, userId, completedRequestId, "hash-a", 1);
		assertThat(replayed).isEqualTo(
			new PendingReading(completed.readingId(), completed.generationId(), false));
		assertSqlState("RL104", () -> createPending(
			jdbcUrl, username, password, userId, completedRequestId, "hash-b", 1));
		assertBalances(jdbcUrl, username, password, userId, 9, 0);

		UUID failedRequestId = UUID.randomUUID();
		PendingReading failed = createPending(
			jdbcUrl, username, password, userId, failedRequestId, "hash-c", 1);
		assertSqlState("RL110", () -> createPending(
			jdbcUrl, username, password, userId, failedRequestId, "hash-c", 1));
		fail(jdbcUrl, username, password, failed);
		assertBalances(jdbcUrl, username, password, userId, 9, 0);
		assertSqlState("RL110", () -> createPending(
			jdbcUrl, username, password, userId, failedRequestId, "hash-c", 1));

		PendingReading independent = createPending(
			jdbcUrl, username, password, userId, UUID.randomUUID(), "hash-independent", 1);
		assertThat(independent.created()).isTrue();
		assertSqlState("RL111", () -> createPending(
			jdbcUrl, username, password, userId, UUID.randomUUID(), "hash-active", 1));
		fail(jdbcUrl, username, password, independent);

		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement(
				 "update public.profiles set paid_credit_balance = 2 where id = ?")) {
			statement.setObject(1, userId);
			statement.executeUpdate();
		}
		PendingReading paid = createPending(
			jdbcUrl, username, password, userId, UUID.randomUUID(), "hash-paid", 10);
		complete(jdbcUrl, username, password, paid);
		assertBalances(jdbcUrl, username, password, userId, 0, 1);
		assertSqlState("RL112", () -> createPending(
			jdbcUrl, username, password, userId, UUID.randomUUID(), "hash-insufficient", 2));

		verifyConcurrentReservation(jdbcUrl, username, password);
		verifyStaleCleanup(jdbcUrl, username, password);
		verifyDailyCapacity(jdbcUrl, username, password);
	}

	private void verifyDailyCapacity(String jdbcUrl, String username, String password)
		throws Exception {
		UUID capacityUser = createProfile(jdbcUrl, username, password);
		int usedToday = capacityUsedToday(jdbcUrl, username, password);
		assertThat(usedToday).isPositive();
		assertSqlState("RL113", () -> createPending(
			jdbcUrl, username, password, capacityUser, UUID.randomUUID(),
			"hash-capacity-full", 1, usedToday));
		assertSqlState("RL113", () -> createPending(
			jdbcUrl, username, password, capacityUser, UUID.randomUUID(),
			"hash-capacity-stopped", 1, 0));
		assertThat(capacityUsedToday(jdbcUrl, username, password)).isEqualTo(usedToday);

		PendingReading allowed = createPending(
			jdbcUrl, username, password, capacityUser, UUID.randomUUID(),
			"hash-capacity-open", 1, usedToday + 1);
		assertThat(allowed.created()).isTrue();
		assertThat(capacityUsedToday(jdbcUrl, username, password)).isEqualTo(usedToday + 1);
		fail(jdbcUrl, username, password, allowed);

		// Deleting a reading must not hand capacity back.
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement(
				 "delete from public.readings where user_id = ?")) {
			statement.setObject(1, capacityUser);
			assertThat(statement.executeUpdate()).isEqualTo(1);
		}
		assertThat(capacityUsedToday(jdbcUrl, username, password)).isEqualTo(usedToday + 1);

		var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
			jdbcUrl, username, password
		);
		var creditRepository =
			new com.myeongro.api.domain.readingcredit.repository.JdbcReadingCreditRepository(
				new org.springframework.jdbc.core.JdbcTemplate(dataSource)
			);
		assertThat(creditRepository.todayAttemptCount()).isEqualTo(usedToday + 1);
	}

	private int capacityUsedToday(String jdbcUrl, String username, String password)
		throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.createStatement();
			 var resultSet = statement.executeQuery("""
				 select attempt_count from public.reading_daily_capacity
				 where usage_date = (current_timestamp at time zone 'Asia/Seoul')::date
				 """)) {
			assertThat(resultSet.next()).isTrue();
			return resultSet.getInt(1);
		}
	}

	private UUID createProfile(String jdbcUrl, String username, String password)
		throws SQLException {
		UUID userId = UUID.randomUUID();
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement(
				 "insert into public.profiles (id) values (?)")) {
			statement.setObject(1, userId);
			statement.executeUpdate();
		}
		return userId;
	}

	private void verifyConcurrentReservation(String jdbcUrl, String username, String password)
		throws Exception {
		UUID userId = createProfile(jdbcUrl, username, password);
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
			 var statement = connection.prepareStatement(
				 "select count(*) from public.readings where user_id = ? and request_id = ?")) {
			statement.setObject(1, userId);
			statement.setObject(2, requestId);
			try (var resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				assertThat(resultSet.getInt(1)).isOne();
			}
		}
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
			try (var lock = blocker.prepareStatement(
				 "select id from public.readings where id = ? for update")) {
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
				jdbcUrl, username, password, userId, requestId, "hash-a", 1));
			awaitAdvisoryWaiter(jdbcUrl, username, password);

			blocker.commit();
			assertThat(completion.get(5, TimeUnit.SECONDS)).isEqualTo("completed");
			assertThat(replay.get(5, TimeUnit.SECONDS)).isEqualTo(
				new PendingReading(pending.readingId(), pending.generationId(), false));
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
				jdbcUrl, username, password, userId, requestId, "hash-concurrent", 1);
			return pending.created() ? "created" : "reused";
		} catch (SQLException exception) {
			return exception.getSQLState();
		}
	}

	private void verifyStaleCleanup(String jdbcUrl, String username, String password)
		throws SQLException {
		UUID userId = createProfile(jdbcUrl, username, password);
		UUID requestId = UUID.randomUUID();
		createPending(jdbcUrl, username, password, userId, requestId, "hash-stale", 1);
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
			try (var age = connection.prepareStatement("""
				 update public.generation_records gr
				 set created_at = current_timestamp - interval '10 minutes'
				 from public.readings r
				 where r.id = gr.reading_id and r.user_id = ? and r.request_id = ?
				 """)) {
				age.setObject(1, userId);
				age.setObject(2, requestId);
				assertThat(age.executeUpdate()).isOne();
			}
			try (var cleanup = connection.prepareStatement(
				"select public.fail_stale_reading_generations(interval '5 minutes')")) {
				try (var resultSet = cleanup.executeQuery()) {
					assertThat(resultSet.next()).isTrue();
					assertThat(resultSet.getInt(1)).isOne();
				}
			}
		}
	}

	private void verifyConsentTransitionSerialization(
		String jdbcUrl,
		String username,
		String password
	) throws Exception {
		UUID userId = createProfile(jdbcUrl, username, password);
		insertConsentEvent(
			DriverManager.getConnection(jdbcUrl, username, password), userId, "WITHDRAWN", true);

		var executor = Executors.newSingleThreadExecutor();
		try (var acceptance = DriverManager.getConnection(jdbcUrl, username, password)) {
			acceptance.setAutoCommit(false);
			lockConsentTransition(acceptance, userId);
			assertThat(latestConsentAction(acceptance, userId)).isEqualTo("WITHDRAWN");

			var withdrawal = executor.submit(() -> {
				try (var connection = DriverManager.getConnection(jdbcUrl, username, password)) {
					connection.setAutoCommit(false);
					lockConsentTransition(connection, userId);
					String observed = latestConsentAction(connection, userId);
					insertConsentEvent(connection, userId, "WITHDRAWN", false);
					connection.commit();
					return observed;
				}
			});
			awaitAdvisoryWaiter(jdbcUrl, username, password);
			insertConsentEvent(acceptance, userId, "ACCEPTED", false);
			acceptance.commit();

			assertThat(withdrawal.get(5, TimeUnit.SECONDS)).isEqualTo("ACCEPTED");
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		}
	}

	private void lockConsentTransition(Connection connection, UUID userId) throws SQLException {
		try (var statement = connection.prepareStatement("""
			 select pg_advisory_xact_lock(
			   hashtextextended('consent:' || cast(? as text) || ':AI_OVERSEAS_TRANSFER', 0)
			 )
			 """)) {
			statement.setObject(1, userId);
			statement.execute();
		}
	}

	private String latestConsentAction(Connection connection, UUID userId) throws SQLException {
		try (var statement = connection.prepareStatement("""
			 select action from public.consent_events
			 where user_id = ? and document_type = 'AI_OVERSEAS_TRANSFER'
			 order by occurred_at desc, id desc limit 1
			 """)) {
			statement.setObject(1, userId);
			try (var resultSet = statement.executeQuery()) {
				assertThat(resultSet.next()).isTrue();
				return resultSet.getString(1);
			}
		}
	}

	private void insertConsentEvent(
		Connection connection,
		UUID userId,
		String action,
		boolean closeConnection
	) throws SQLException {
		try {
			try (var statement = connection.prepareStatement("""
				 insert into public.consent_events (
				   user_id, document_type, document_version, action, occurred_at, method
				 ) values (?, 'AI_OVERSEAS_TRANSFER', '2026-09-20', ?, clock_timestamp(), 'test')
				 """)) {
				statement.setObject(1, userId);
				statement.setString(2, action);
				statement.executeUpdate();
			}
		} finally {
			if (closeConnection) {
				connection.close();
			}
		}
	}

	private void awaitAdvisoryWaiter(String jdbcUrl, String username, String password)
		throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
				 var statement = connection.createStatement();
				 var resultSet = statement.executeQuery("""
					 select exists (
					   select 1 from pg_locks where locktype = 'advisory' and not granted
					 )
					 """)) {
				assertThat(resultSet.next()).isTrue();
				if (resultSet.getBoolean(1)) {
					return;
				}
			}
			Thread.sleep(25);
		}
		throw new AssertionError("Consent transition did not wait for the advisory lock");
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
		return createPending(
			jdbcUrl, username, password, userId, requestId, inputHash, creditCost, 100_000
		);
	}

	private PendingReading createPending(
		String jdbcUrl,
		String username,
		String password,
		UUID userId,
		UUID requestId,
		String inputHash,
		int creditCost,
		int globalDailyAttemptLimit
	) throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement("""
				 select reading_id, generation_id, created
				 from public.create_pending_reading(
				   ?, ?, ?, cast(? as public.reading_kind), ?, ?, cast(? as jsonb),
				   ?, ?, ?, ?, ?, ?
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
			statement.setInt(13, globalDailyAttemptLimit);
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
			 var statement = connection.prepareStatement(
				 "select public.complete_reading_generation(?, ?, ?, cast(? as jsonb), ?)")) {
			statement.setObject(1, pending.readingId());
			statement.setLong(2, pending.generationId());
			statement.setString(3, "Completed reading");
			statement.setString(4, "{}");
			statement.setInt(5, 10);
			statement.execute();
		}
	}

	private void fail(String jdbcUrl, String username, String password, PendingReading pending)
		throws SQLException {
		try (var connection = DriverManager.getConnection(jdbcUrl, username, password);
			 var statement = connection.prepareStatement(
				 "select public.fail_reading_generation(?, ?, ?)")) {
			statement.setObject(1, pending.readingId());
			statement.setLong(2, pending.generationId());
			statement.setString(3, "TEST_FAILURE");
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

	private void assertSqlState(String expected, SqlOperation operation) {
		try {
			operation.run();
		} catch (SQLException exception) {
			assertThat(exception.getSQLState()).isEqualTo(expected);
			return;
		}
		throw new AssertionError("Expected SQL state " + expected);
	}

	private void verifyAnnualReportContract(String url, String username, String password) throws Exception {
		var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
		var snapshot = new java.util.LinkedHashMap<String, Object>();
		snapshot.put("calculationVersion", "saju-ko-v4"); snapshot.put("engine", "lunar-java");
		snapshot.put("engineVersion", "1.7.7"); snapshot.put("cityCatalogVersion", "kr-admin-v1-province");
		snapshot.put("targetYear", 2027);
		var pillars = new java.util.LinkedHashMap<String, Object>();
		for (String name : List.of("year", "month", "day", "time")) pillars.put(name, null);
		snapshot.put("pillars", pillars); snapshot.put("fiveElements", java.util.Map.of()); snapshot.put("relations", List.of());
		snapshot.put("limitations", List.of()); snapshot.put("uncertainty", java.util.Map.of("varyingFields", List.of()));
		snapshot.put("monthlyCalendar", new com.myeongro.api.domain.saju.calculation.SajuMonthlyCalendarService().calculate(2027, null));
		var payload = new java.util.LinkedHashMap<String, Object>();
		payload.put("targetYear", 2027); payload.put("calculationSnapshot", snapshot); payload.put("reportType", "annual_monthly");
		try (var connection = DriverManager.getConnection(url, username, password);
			var check = connection.prepareStatement("select public.is_valid_saju_v7_input(?::jsonb), public.is_valid_saju_v6_input(?::jsonb), public.is_valid_saju_v7_input(?::jsonb)")) {
			check.setString(1, mapper.writeValueAsString(payload)); check.setString(2, mapper.writeValueAsString(payload));
			var withoutType = new java.util.LinkedHashMap<>(payload); withoutType.remove("reportType");
			check.setString(3, mapper.writeValueAsString(withoutType));
			try (var row = check.executeQuery()) { assertThat(row.next()).isTrue(); assertThat(row.getBoolean(1)).isTrue(); assertThat(row.getBoolean(2)).isFalse(); assertThat(row.getBoolean(3)).isFalse(); }
		}
		UUID owner = createProfile(url, username, password);
		for (boolean succeeds : List.of(false, true)) {
			PendingReading pending;
			UUID request = UUID.randomUUID();
			try (var connection = DriverManager.getConnection(url, username, password);
				var query = connection.prepareStatement("select * from public.create_pending_reading(?, ?, 'annual-hash', 'saju'::public.reading_kind, null, 7, ?::jsonb, 'openai', 'test-model', 'annual-test', 4, 10, 1000)")) {
				query.setObject(1, owner); query.setObject(2, request); query.setString(3, mapper.writeValueAsString(payload));
				try (var row = query.executeQuery()) { assertThat(row.next()).isTrue(); pending = new PendingReading(row.getObject("reading_id", UUID.class), row.getLong("generation_id"), row.getBoolean("created")); }
			}
			assertBalances(url, username, password, owner, 10, 0);
			if (succeeds) complete(url, username, password, pending); else fail(url, username, password, pending);
			assertBalances(url, username, password, owner, succeeds ? 6 : 10, 0);
			try (var connection = DriverManager.getConnection(url, username, password);
				var query = connection.prepareStatement("select status::text, schema_version, input_payload = ?::jsonb from public.readings where id = ?")) {
				query.setString(1, mapper.writeValueAsString(payload)); query.setObject(2, pending.readingId());
				try (var row = query.executeQuery()) { assertThat(row.next()).isTrue(); assertThat(row.getString(1)).isEqualTo(succeeds ? "completed" : "failed"); assertThat(row.getInt(2)).isEqualTo(7); assertThat(row.getBoolean(3)).isTrue(); }
			}
		}
	}

	private record PendingReading(UUID readingId, long generationId, boolean created) {
	}

	@FunctionalInterface
	private interface SqlOperation {
		void run() throws SQLException;
	}
}
