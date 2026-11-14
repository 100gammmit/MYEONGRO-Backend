package com.myeongro.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Indexed Spring Session configures Redis keyspace notifications at startup, so the full
 * context needs a real Redis. Runs only when {@code freshRedisHost} points at one.
 */
@SpringBootTest
@EnabledIfSystemProperty(
	named = "freshRedisHost",
	matches = ".+",
	disabledReason = "Set freshRedisHost (and optionally freshRedisPort) to run against a real Redis")
class MyeongroBackendApplicationTests {

	@DynamicPropertySource
	static void freshRedis(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", () -> System.getProperty("freshRedisHost"));
		registry.add("spring.data.redis.port", MyeongroBackendApplicationTests::freshRedisPort);
	}

	private static String freshRedisPort() {
		String port = System.getProperty("freshRedisPort");
		return port == null || port.isBlank() ? "6379" : port;
	}

	@Test
	void contextLoads() {
	}

}
