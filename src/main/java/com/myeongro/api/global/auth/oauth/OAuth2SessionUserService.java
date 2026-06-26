package com.myeongro.api.global.auth.oauth;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class OAuth2SessionUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
	private final OAuthUserProvisioner provisioner;
	private final List<OAuthProviderUserInfoExtractor> extractors;

	@Autowired
	public OAuth2SessionUserService(
		OAuthUserProvisioner provisioner,
		List<OAuthProviderUserInfoExtractor> extractors
	) {
		this(new DefaultOAuth2UserService(), provisioner, extractors);
	}

	OAuth2SessionUserService(
		OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate,
		OAuthUserProvisioner provisioner,
		List<OAuthProviderUserInfoExtractor> extractors
	) {
		this.delegate = delegate;
		this.provisioner = provisioner;
		this.extractors = List.copyOf(extractors);
	}

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oauthUser = delegate.loadUser(userRequest);
		String registrationId = userRequest == null
			? "kakao"
			: userRequest.getClientRegistration().getRegistrationId();
		OAuthProviderUserInfo userInfo = extractors.stream()
			.filter(extractor -> extractor.supports(registrationId))
			.findFirst()
			.orElseThrow(() -> new OAuth2AuthenticationException(
				"Unsupported OAuth provider: " + registrationId
			))
			.extract(oauthUser.getAttributes());
		ProvisionedOAuthUser provisionedUser = provisioner.provision(userInfo);
		return new SessionOAuth2User(
			provisionedUser,
			oauthUser.getAttributes(),
			oauthUser.getAuthorities()
		);
	}
}
