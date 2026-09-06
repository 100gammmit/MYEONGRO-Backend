package com.myeongro.api.domain.consent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import com.myeongro.api.domain.consent.entity.ConsentAction;
import com.myeongro.api.domain.consent.entity.ConsentDocumentType;
import com.myeongro.api.domain.consent.entity.ConsentEventEntity;

@DataJpaTest
@TestPropertySource(properties = {
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.flyway.enabled=false",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:consent-schema.sql"
})
class ConsentEventRepositoryTests {

	private static final UUID USER_ID =
		UUID.fromString("3b413be2-2b81-4802-8c6a-f868a85d8d83");

	@Autowired
	private ConsentEventRepository repository;

	@Test
	void preservesAcceptedAndWithdrawnEventsInLatestFirstOrder() {
		repository.save(ConsentEventEntity.accepted(
			USER_ID,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER,
			"draft-2026-09-07",
			Instant.parse("2026-09-07T00:00:00Z")
		));
		repository.save(ConsentEventEntity.withdrawn(
			USER_ID,
			ConsentDocumentType.AI_OVERSEAS_TRANSFER,
			"draft-2026-09-07",
			Instant.parse("2026-09-07T01:00:00Z")
		));

		var events = repository.findAllByUserIdOrderByOccurredAtDescIdDesc(USER_ID);

		assertThat(events).hasSize(2);
		assertThat(events.get(0).getAction()).isEqualTo(ConsentAction.WITHDRAWN);
		assertThat(events.get(1).getAction()).isEqualTo(ConsentAction.ACCEPTED);
	}
}
