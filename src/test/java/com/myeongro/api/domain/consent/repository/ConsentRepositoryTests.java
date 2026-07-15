package com.myeongro.api.domain.consent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.myeongro.api.domain.consent.entity.ConsentEntity;

@DataJpaTest
@TestPropertySource(properties = {
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.flyway.enabled=false",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:consent-schema.sql"
})
class ConsentRepositoryTests {

	private static final UUID USER_ID =
		UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");

	@Autowired
	private ConsentRepository repository;

	@Test
	void savesAndFindsOneUserConsentRowForAllRequiredDocuments() {
		ConsentEntity consent = ConsentEntity.acceptedForUser(
			USER_ID,
			"2026-06-10",
			"2026-06-10",
			"2026-06-10",
			Instant.parse("2026-06-15T00:00:00Z")
		);

		repository.save(consent);

		Optional<ConsentEntity> found = repository.findByUserId(USER_ID);
		assertThat(found).isPresent();
		assertThat(found.get().hasAcceptedCurrentVersions(
			"2026-06-10",
			"2026-06-10",
			"2026-06-10"
		)).isTrue();
		assertThat(found.get().getPrivacyAcceptedAt())
			.isEqualTo(Instant.parse("2026-06-15T00:00:00Z"));
	}
}
