package com.myeongro.api.global.auth;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.myeongro.api.domain.profile.repository.ProfileJpaRepository;
import com.myeongro.api.global.auth.session.SessionPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class ActiveAccountSessionFilter extends OncePerRequestFilter {

	private final ProfileJpaRepository profileRepository;

	public ActiveAccountSessionFilter(ProfileJpaRepository profileRepository) {
		this.profileRepository = profileRepository;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null
			&& authentication.isAuthenticated()
			&& authentication.getPrincipal() instanceof SessionPrincipal principal
			&& !profileRepository.existsById(principal.userId())) {
			SecurityContextHolder.clearContext();
			HttpSession session = request.getSession(false);
			if (session != null) {
				session.invalidate();
			}
		}
		filterChain.doFilter(request, response);
	}
}
