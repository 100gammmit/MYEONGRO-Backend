package com.myeongro.api.domain.consent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEntity;

@DataJpaTest
@TestPropertySource(properties = {
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.flyway.enabled=false",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:consent-schema.sql"
})
class ConsentRepositoryTests {

	private static final UUID GUEST_ID =
		UUID.fromString("9775ff70-5708-45d8-85f8-cb57878bc25d");

	@Autowired
	private ConsentRepository repository;

	@Test
	void savesAndFindsGuestConsents() {
		ConsentEntity consent = ConsentEntity.forGuest(
			GUEST_ID,
			ConsentDocumentType.TERMS,
			"2026-06-10",
			Instant.parse("2026-06-15T00:00:00Z")
		);

		repository.save(consent);

		List<ConsentEntity> found = repository.findAllByGuestSessionId(GUEST_ID);
		assertThat(found).hasSize(1);
		assertThat(found.getFirst().getDocumentType())
			.isEqualTo(ConsentDocumentType.TERMS);
		assertThat(found.getFirst().getAcceptedAt())
			.isEqualTo(Instant.parse("2026-06-15T00:00:00Z"));
	}
}
