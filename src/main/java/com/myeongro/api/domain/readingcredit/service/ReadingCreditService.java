package com.myeongro.api.domain.readingcredit.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.myeongro.api.domain.readingcredit.config.ReadingCreditProperties;
import com.myeongro.api.domain.readingcredit.dto.ReadingCreditStatusResponse;
import com.myeongro.api.domain.readingcredit.repository.ReadingCreditRepository;
import com.myeongro.api.domain.tarot.model.TarotSpreadType;

@Service
public class ReadingCreditService {

	private static final ZoneId CREDIT_ZONE = ZoneId.of("Asia/Seoul");

	private final ReadingCreditRepository repository;
	private final ReadingCreditProperties properties;
	private final Clock clock;

	@Autowired
	public ReadingCreditService(
		ReadingCreditRepository repository,
		ReadingCreditProperties properties
	) {
		this(repository, properties, Clock.systemUTC());
	}

	ReadingCreditService(
		ReadingCreditRepository repository,
		ReadingCreditProperties properties,
		Clock clock
	) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	public ReadingCreditStatusResponse getStatus(UUID userId) {
		var snapshot = repository.getStatus(userId, properties.dailyFreeGrant());
		return new ReadingCreditStatusResponse(
			properties.dailyFreeGrant(),
			ReadingCreditStatusResponse.Balance.of(
				snapshot.freeBalance(), snapshot.paidBalance()
			),
			nextResetAt(),
			snapshot.generationInProgress(),
			new ReadingCreditStatusResponse.Costs(tarotCosts(), properties.costs().saju())
		);
	}

	private Map<String, Integer> tarotCosts() {
		Map<String, Integer> costs = new LinkedHashMap<>();
		for (TarotSpreadType spreadType : TarotSpreadType.values()) {
			costs.put(spreadType.value(), properties.costs().tarot().cost(spreadType));
		}
		return Map.copyOf(costs);
	}

	private Instant nextResetAt() {
		ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), CREDIT_ZONE);
		return now.toLocalDate().plusDays(1).atStartOfDay(CREDIT_ZONE).toInstant();
	}
}
