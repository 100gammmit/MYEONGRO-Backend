package com.myeongro.api.global.auth.oauth;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class OidcSessionUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
	private final OAuthUserProvisioner provisioner;
	private final List<OAuthProviderUserInfoExtractor> extractors;

	@Autowired
	public OidcSessionUserService(
		OAuthUserProvisioner provisioner,
		List<OAuthProviderUserInfoExtractor> extractors
	) {
		this(new OidcUserService(), provisioner, extractors);
	}

	OidcSessionUserService(
		OAuth2UserService<OidcUserRequest, OidcUser> delegate,
		OAuthUserProvisioner provisioner,
		List<OAuthProviderUserInfoExtractor> extractors
	) {
		this.delegate = delegate;
		this.provisioner = provisioner;
		this.extractors = List.copyOf(extractors);
	}

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
		String registrationId = userRequest.getClientRegistration().getRegistrationId();
		OAuthProviderUserInfoExtractor selectedExtractor = extractors.stream()
			.filter(extractor -> extractor.supports(registrationId))
			.findFirst()
			.orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(
				"unsupported_oidc_provider",
				"Unsupported OIDC provider: " + registrationId,
				null
			)));
		OidcUser oidcUser = delegate.loadUser(userRequest);
		OAuthProviderUserInfo userInfo = selectedExtractor.extract(oidcUser.getAttributes());
		ProvisionedOAuthUser provisionedUser = provisioner.provision(userInfo);
		return new SessionOidcUser(provisionedUser, oidcUser);
	}
}
