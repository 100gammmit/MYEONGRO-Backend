package com.myeongro.api.domain.profile.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myeongro.api.domain.profile.service.AccountWithdrawalService;
import com.myeongro.api.global.auth.AuthenticatedUser;
import com.myeongro.api.global.auth.AuthenticatedUserResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/account")
public class AccountController {

	private final AuthenticatedUserResolver userResolver;
	private final AccountWithdrawalService withdrawalService;

	public AccountController(
		AuthenticatedUserResolver userResolver,
		AccountWithdrawalService withdrawalService
	) {
		this.userResolver = userResolver;
		this.withdrawalService = withdrawalService;
	}

	@DeleteMapping
	public ResponseEntity<Void> withdraw(
		Authentication authentication,
		HttpServletRequest request
	) {
		AuthenticatedUser user = userResolver.requireUser(authentication);
		withdrawalService.withdraw(user.id());
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		return ResponseEntity.noContent().build();
	}
}
