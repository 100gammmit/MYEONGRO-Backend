package com.myeongro.testsupport;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityConfigTestEndpoint {

	@PostMapping("/api/readings")
	String createReading() {
		return "created";
	}

	@GetMapping("/api/readings")
	String listReadings() {
		return "records";
	}

	@GetMapping("/api/auth/me")
	String me() {
		return "me";
	}

	@PostMapping("/api/auth/logout")
	ResponseEntity<Void> logout() {
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/internal/security-test")
	String secure() {
		return "ok";
	}
}
