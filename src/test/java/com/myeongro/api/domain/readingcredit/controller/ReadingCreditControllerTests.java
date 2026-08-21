package com.myeongro.api.domain.readingcredit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import com.myeongro.api.domain.readingcredit.dto.ReadingCreditStatusResponse;
import com.myeongro.api.domain.readingcredit.service.ReadingCreditService;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

class ReadingCreditControllerTests {

	@Test
	void returnsAuthenticatedStatusWithoutAllowingCaching() {
		UUID userId = UUID.randomUUID();
		ReadingCreditService service = org.mockito.Mockito.mock(ReadingCreditService.class);
		AuthenticatedUserResolver resolver = org.mockito.Mockito.mock(
			AuthenticatedUserResolver.class
		);
		TestingAuthenticationToken authentication = new TestingAuthenticationToken(
			"user", "password"
		);
		var status = new ReadingCreditStatusResponse(
			10,
			ReadingCreditStatusResponse.Balance.of(7, 5),
			Instant.parse("2026-08-21T15:00:00Z"),
			false,
			new ReadingCreditStatusResponse.Costs(Map.of("daily_one_card", 1), 4)
		);
		when(resolver.requireUser(authentication)).thenReturn(new AuthenticatedUser(userId));
		when(service.getStatus(userId)).thenReturn(status);
		var controller = new ReadingCreditController(service, resolver);

		var response = controller.getStatus(authentication);

		assertThat(response.getBody()).isEqualTo(status);
		assertThat(response.getHeaders().getCacheControl()).contains("no-store");
		verify(service).getStatus(userId);
	}
}
