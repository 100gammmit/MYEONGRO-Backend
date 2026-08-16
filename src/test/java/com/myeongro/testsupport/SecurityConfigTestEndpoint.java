package com.myeongro.testsupport;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityConfigTestEndpoint {

	@PostMapping("/api/tarot/readings")
	String createTarotReading() {
		return "tarot-created";
	}

	@PostMapping("/api/saju/readings")
	String createSajuReading() {
		return "saju-created";
	}

	@GetMapping("/api/readings")
	String listReadings() {
		return "records";
	}

	@GetMapping("/api/auth/me")
	String me() {
		return "me";
	}

	@GetMapping("/actuator/health/readiness")
	String readiness() {
		return "UP";
	}

	@PostMapping("/api/auth/logout")
	ResponseEntity<Void> logout() {
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/account")
	ResponseEntity<Void> deleteAccount() {
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/internal/security-test")
	String secure() {
		return "ok";
	}
}
