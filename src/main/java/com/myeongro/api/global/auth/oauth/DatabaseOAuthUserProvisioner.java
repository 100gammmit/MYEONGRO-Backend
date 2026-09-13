package com.myeongro.api.global.auth.oauth;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.profile.repository.OAuthAccountRepository;

@Component
public class DatabaseOAuthUserProvisioner implements OAuthUserProvisioner {

	private final OAuthAccountRepository repository;

	public DatabaseOAuthUserProvisioner(OAuthAccountRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<ProvisionedOAuthUser> findExisting(OAuthProviderUserInfo userInfo) {
		return repository.findExisting(userInfo);
	}

	@Override
	public ProvisionedOAuthUser provision(OAuthProviderUserInfo userInfo) {
		return repository.provision(userInfo);
	}
}
