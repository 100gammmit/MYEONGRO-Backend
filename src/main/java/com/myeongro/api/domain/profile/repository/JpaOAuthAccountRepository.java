package com.myeongro.api.domain.profile.repository;

import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.myeongro.api.domain.profile.entity.OAuthAccountEntity;
import com.myeongro.api.domain.profile.entity.ProfileEntity;
import com.myeongro.api.global.auth.oauth.OAuthProviderUserInfo;
import com.myeongro.api.global.auth.oauth.ProvisionedOAuthUser;

@Primary
@Repository
public class JpaOAuthAccountRepository implements OAuthAccountRepository {

	private final OAuthAccountLock accountLock;
	private final ProfileJpaRepository profileRepository;
	private final OAuthAccountJpaRepository accountRepository;

	public JpaOAuthAccountRepository(
		OAuthAccountLock accountLock,
		ProfileJpaRepository profileRepository,
		OAuthAccountJpaRepository accountRepository
	) {
		this.accountLock = accountLock;
		this.profileRepository = profileRepository;
		this.accountRepository = accountRepository;
	}

	@Override
	@Transactional
	public ProvisionedOAuthUser provision(OAuthProviderUserInfo userInfo) {
		accountLock.lock(userInfo);
		return accountRepository.findActiveByProviderAccount(
			userInfo.provider(),
			userInfo.providerUserId()
		)
			.map(this::toUser)
			.orElseGet(() -> createAccount(userInfo));
	}

	private ProvisionedOAuthUser createAccount(OAuthProviderUserInfo userInfo) {
		ProfileEntity profile = profileRepository.save(
			ProfileEntity.create(UUID.randomUUID(), userInfo.displayName())
		);
		OAuthAccountEntity account = accountRepository.save(
			OAuthAccountEntity.create(
				profile,
				userInfo.provider(),
				userInfo.providerUserId(),
				userInfo.email(),
				userInfo.displayName()
			)
		);
		return toUser(account);
	}

	private ProvisionedOAuthUser toUser(OAuthAccountEntity account) {
		ProfileEntity profile = account.getProfile();
		return new ProvisionedOAuthUser(
			profile.getId(),
			profile.getDisplayName() == null ? account.getDisplayName() : profile.getDisplayName(),
			account.getProvider(),
			account.getProviderUserId()
		);
	}
}
