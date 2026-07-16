package com.myeongro.api.domain.tarotdraw.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.tarotdraw.exception.TarotDrawSessionException;
import com.myeongro.api.domain.tarotdraw.service.SecureTarotDrawEntropy;
import com.myeongro.api.domain.tarotdraw.service.TarotDrawSessionProperties;
import com.myeongro.api.domain.tarotdraw.service.TarotDrawSessionService;
import com.myeongro.api.domain.tarotdraw.service.TarotDrawSessionView;

class RedisTarotDrawSessionRepositoryIntegrationTests {

	private static final UUID USER_ID = UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");
	private LettuceConnectionFactory connectionFactory;
	private StringRedisTemplate redis;
	private String namespace;

	@BeforeEach
	void setUp() {
		String host = System.getProperty("freshRedisHost");
		Assumptions.assumeTrue(host != null, "Set freshRedisHost to run Redis integration tests");
		int port = Integer.parseInt(System.getProperty("freshRedisPort", "6379"));
		connectionFactory = new LettuceConnectionFactory(host, port);
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();
		redis = new StringRedisTemplate(connectionFactory);
		redis.afterPropertiesSet();
		namespace = "myeongro:test:tarot-draw:" + UUID.randomUUID();
	}

	@AfterEach
	void tearDown() {
		if (redis != null) {
			var keys = redis.keys(namespace + "*");
			if (keys != null && !keys.isEmpty()) redis.delete(keys);
		}
		if (connectionFactory != null) connectionFactory.destroy();
	}

	@Test
	void onlyOneConcurrentSelectionAdvancesThePosition() throws Exception {
		TarotDrawSessionService service = service(Duration.ofMinutes(30));
		TarotDrawSessionView created = service.create(USER_ID, "mind_three_card");
		String token = created.candidates().get(0).token();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Object> first = executor.submit(() -> select(service, created.drawSessionId(), token, ready, start));
			Future<Object> second = executor.submit(() -> select(service, created.drawSessionId(), token, ready, start));
			ready.await();
			start.countDown();

			List<Object> results = List.of(first.get(), second.get());
			assertThat(results).filteredOn(TarotDrawSessionView.class::isInstance).hasSize(1);
			assertThat(results).filteredOn(TarotDrawSessionException.class::isInstance).hasSize(1);
			assertThat(service.getActive(USER_ID).selectedCount()).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void onlyOneConcurrentCreationBecomesActive() throws Exception {
		TarotDrawSessionService service = service(Duration.ofMinutes(30));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Object> first = executor.submit(() -> create(service, ready, start));
			Future<Object> second = executor.submit(() -> create(service, ready, start));
			ready.await();
			start.countDown();

			List<Object> results = List.of(first.get(), second.get());
			assertThat(results).filteredOn(TarotDrawSessionView.class::isInstance).hasSize(1);
			assertThat(results).filteredOn(TarotDrawSessionException.class::isInstance).hasSize(1);
			assertThat(service.getActive(USER_ID).status()).isEqualTo("in_progress");
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void hidesNonOwnedSessionAndExpiresBothSessionAndActivePointer() throws Exception {
		TarotDrawSessionService service = service(Duration.ofMillis(150));
		TarotDrawSessionView created = service.create(USER_ID, "daily_one_card");

		assertThatThrownBy(() -> service.select(UUID.randomUUID(), created.drawSessionId(), "token"))
			.isInstanceOfSatisfying(TarotDrawSessionException.class, exception ->
				assertThat(exception.code()).isEqualTo("DRAW_SESSION_NOT_FOUND"));

		Thread.sleep(300);
		assertThatThrownBy(() -> service.getActive(USER_ID))
			.isInstanceOfSatisfying(TarotDrawSessionException.class, exception ->
				assertThat(exception.code()).isEqualTo("DRAW_SESSION_NOT_FOUND"));
		assertThat(redis.keys(namespace + "*")).isEmpty();
	}

	@Test
	void persistsConsumedStateForSameRequestAndRejectsAnotherRequest() {
		TarotDrawSessionService service = service(Duration.ofMinutes(30));
		TarotDrawSessionView created = service.create(USER_ID, "daily_one_card");
		service.select(USER_ID, created.drawSessionId(), created.candidates().get(0).token());
		UUID requestId = UUID.randomUUID();

		service.resolveAndConsume(USER_ID, created.drawSessionId(), "daily_one_card", requestId, "hash");
		assertThat(service.getActive(USER_ID).status()).isEqualTo("complete");
		assertThat(service.resolveAndConsume(
			USER_ID, created.drawSessionId(), "daily_one_card", requestId, "hash"
		).cardIds()).hasSize(1);
		assertThatThrownBy(() -> service.resolveAndConsume(
			USER_ID, created.drawSessionId(), "daily_one_card", UUID.randomUUID(), "hash"
		)).isInstanceOfSatisfying(TarotDrawSessionException.class, exception ->
			assertThat(exception.code()).isEqualTo("DRAW_SESSION_ALREADY_CONSUMED"));

		assertThat(service.finalizeConsumption(
			USER_ID, created.drawSessionId(), requestId, "hash"
		)).isTrue();
		assertThatThrownBy(() -> service.getActive(USER_ID))
			.isInstanceOfSatisfying(TarotDrawSessionException.class, exception ->
				assertThat(exception.code()).isEqualTo("DRAW_SESSION_NOT_FOUND"));
		assertThat(service.resolveAndConsume(
			USER_ID, created.drawSessionId(), "daily_one_card", requestId, "hash"
		).cardIds()).hasSize(1);
	}

	private Object select(
		TarotDrawSessionService service,
		String sessionId,
		String token,
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		start.await();
		try {
			return service.select(USER_ID, sessionId, token);
		} catch (TarotDrawSessionException exception) {
			return exception;
		}
	}

	private Object create(
		TarotDrawSessionService service,
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		start.await();
		try {
			return service.create(USER_ID, "daily_one_card");
		} catch (TarotDrawSessionException exception) {
			return exception;
		}
	}

	private TarotDrawSessionService service(Duration ttl) {
		TarotDrawSessionProperties properties = new TarotDrawSessionProperties(namespace, ttl);
		ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
		return new TarotDrawSessionService(
			new RedisTarotDrawSessionRepository(redis, objectMapper, properties),
			new SecureTarotDrawEntropy(),
			properties
		);
	}
}
