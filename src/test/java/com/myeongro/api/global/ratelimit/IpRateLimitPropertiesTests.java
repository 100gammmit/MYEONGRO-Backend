package com.myeongro.api.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;

class IpRateLimitPropertiesTests {
	@TestConfiguration(proxyBeanMethods=false)
	@EnableConfigurationProperties(IpRateLimitProperties.class)
	static class Binding { }
	private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(Binding.class)
		.withPropertyValues("app.rate-limit.oauth-start-per-minute=60","app.rate-limit.oauth-callback-per-minute=60");
	@Test void publicScopeDefaultsToTwentyAndCanBeConfiguredWithoutChangingOAuth() {
		runner.run(context->{
			assertThat(context).hasNotFailed();
			var properties=context.getBean(IpRateLimitProperties.class);
			assertThat(properties.sajuCalculationPerMinute()).isEqualTo(20);
			assertThat(properties.oauthStartPerMinute()).isEqualTo(60);
		});
		runner.withPropertyValues("app.rate-limit.saju-calculation-per-minute=15").run(context->{
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(IpRateLimitProperties.class).sajuCalculationPerMinute()).isEqualTo(15);
		});
	}
}
