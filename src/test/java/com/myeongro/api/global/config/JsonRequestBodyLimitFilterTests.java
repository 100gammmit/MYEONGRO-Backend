package com.myeongro.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JsonRequestBodyLimitFilterTests {

	private final JsonRequestBodyLimitFilter filter = new JsonRequestBodyLimitFilter();

	@Test
	void rejectsUnknownLengthJsonBeforeCallingDownstream() throws Exception {
		byte[] body = new byte[JsonRequestBodyLimitFilter.MAX_BYTES + 1];
		var response = new MockHttpServletResponse();

		filter.doFilter(requestWithUnknownLength(body), response, (request, ignored) -> {
			throw new AssertionError("Oversized JSON reached downstream");
		});

		assertThat(response.getStatus()).isEqualTo(413);
	}

	@Test
	void forwardsExactlyTheLimitWithoutChangingTheBytes() throws Exception {
		byte[] body = (" ".repeat(JsonRequestBodyLimitFilter.MAX_BYTES - 2) + "{}")
			.getBytes(StandardCharsets.UTF_8);
		var response = new MockHttpServletResponse();

		filter.doFilter(requestWithUnknownLength(body), response, (request, ignored) ->
			assertThat(request.getInputStream().readAllBytes()).isEqualTo(body));

		assertThat(response.getStatus()).isEqualTo(200);
	}

	private static MockHttpServletRequest requestWithUnknownLength(byte[] body) {
		var request = new MockHttpServletRequest("POST", "/api/tarot/daily-card-selections") {
			@Override
			public int getContentLength() {
				return -1;
			}

			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		request.setContentType("application/vnd.myeongro+json; charset=UTF-8");
		request.setContent(body);
		return request;
	}
}
