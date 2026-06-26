package com.myeongro.api.global.auth.oauth;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.profile.repository.OAuthAccountRepository;

@Component
public class DatabaseOAuthUserProvisioner implements OAuthUserProvisioner {

	private final OAuthAccountRepository repository;

	public DatabaseOAuthUserProvisioner(OAuthAccountRepository repository) {
		this.repository = repository;
	}

	@Override
	public ProvisionedOAuthUser provision(OAuthProviderUserInfo userInfo) {
		return repository.provision(userInfo);
	}
}
