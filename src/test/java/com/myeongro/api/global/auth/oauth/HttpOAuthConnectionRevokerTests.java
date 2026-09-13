package com.myeongro.api.global.auth.oauth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class HttpOAuthConnectionRevokerTests {

	@Test
	void unlinksAKakaoConnectionWithThePendingAccessToken() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo("https://kapi.kakao.com/v1/user/unlink"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer kakao-token"))
			.andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
			.andRespond(withNoContent());

		new HttpOAuthConnectionRevoker(builder).revoke("kakao", "kakao-token");

		server.verify();
	}

	@Test
	void revokesAGoogleConnectionWithAFormEncodedToken() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(once(), requestTo("https://oauth2.googleapis.com/revoke"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE))
			.andExpect(content().string("token=google+token%2Fwith%3Freserved"))
			.andRespond(withNoContent());

		new HttpOAuthConnectionRevoker(builder).revoke(
			"google",
			"google token/with?reserved"
		);

		server.verify();
	}

	@Test
	void convertsProviderFailuresIntoAStableDomainException() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://kapi.kakao.com/v1/user/unlink"))
			.andRespond(withServerError());

		assertThatThrownBy(
			() -> new HttpOAuthConnectionRevoker(builder).revoke("kakao", "token")
		).isInstanceOf(OAuthConnectionRevocationException.class);

		server.verify();
	}
}
