package com.myeongro.api.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;

class RedisSessionNamespaceMigrationTests {

	@Test
	void deploymentNamespaceBumpInvalidatesSessionsWrittenByTheDefaultRepository() {
		String host = System.getProperty("freshRedisHost");
		String portValue = System.getProperty("freshRedisPort");
		assumeTrue(host != null && portValue != null,
			"Set freshRedisHost and freshRedisPort to run");

		LettuceConnectionFactory connectionFactory =
			new LettuceConnectionFactory(host, Integer.parseInt(portValue));
		connectionFactory.afterPropertiesSet();
		connectionFactory.start();

		RedisTemplate<String, Object> redisOperations = new RedisTemplate<>();
		redisOperations.setConnectionFactory(connectionFactory);
		redisOperations.afterPropertiesSet();

		RedisSessionRepository previousRepository =
			new RedisSessionRepository(redisOperations);
		previousRepository.setRedisKeyNamespace("myeongro:session");
		Session previousSession = createAndSave(previousRepository);

		try {
			assertThat(previousRepository.findById(previousSession.getId())).isNotNull();

			RedisIndexedSessionRepository currentRepository =
				new RedisIndexedSessionRepository(redisOperations);
			currentRepository.setRedisKeyNamespace("myeongro:session:v2");

			assertThat(currentRepository.findById(previousSession.getId())).isNull();
		} finally {
			previousRepository.deleteById(previousSession.getId());
			connectionFactory.destroy();
		}
	}

	private static <S extends Session> S createAndSave(SessionRepository<S> repository) {
		S session = repository.createSession();
		repository.save(session);
		return session;
	}
}
