package com.myeongro.api.global.auth.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpOAuthConnectionRevoker implements OAuthConnectionRevoker {

	private static final String KAKAO_UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";
	private static final String GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke";

	private final RestClient restClient;

	public HttpOAuthConnectionRevoker(RestClient.Builder restClientBuilder) {
		this.restClient = restClientBuilder.build();
	}

	@Override
	public void revoke(String provider, String accessToken) {
		try {
			switch (provider) {
				case "kakao" -> revokeKakao(accessToken);
				case "google" -> revokeGoogle(accessToken);
				default -> throw new IllegalArgumentException(
					"Unsupported OAuth provider: " + provider
				);
			}
		} catch (OAuthConnectionRevocationException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new OAuthConnectionRevocationException(provider, exception);
		}
	}

	private void revokeKakao(String accessToken) {
		restClient.post()
			.uri(KAKAO_UNLINK_URL)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.retrieve()
			.toBodilessEntity();
	}

	private void revokeGoogle(String accessToken) {
		String body = "token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
		restClient.post()
			.uri(GOOGLE_REVOKE_URL)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(body)
			.retrieve()
			.toBodilessEntity();
	}
}
