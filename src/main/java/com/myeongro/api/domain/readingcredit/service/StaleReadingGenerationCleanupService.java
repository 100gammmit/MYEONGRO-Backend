package com.myeongro.api.domain.readingcredit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.myeongro.api.domain.readingcredit.config.ReadingCreditProperties;
import com.myeongro.api.domain.readingcredit.repository.ReadingCreditRepository;

@Service
public class StaleReadingGenerationCleanupService {

	private static final Logger log = LoggerFactory.getLogger(
		StaleReadingGenerationCleanupService.class
	);

	private final ReadingCreditRepository repository;
	private final ReadingCreditProperties properties;

	public StaleReadingGenerationCleanupService(
		ReadingCreditRepository repository,
		ReadingCreditProperties properties
	) {
		this.repository = repository;
		this.properties = properties;
	}

	@Scheduled(cron = "${app.reading-credits.cleanup-cron}")
	public void failStaleGenerations() {
		int count = repository.failStaleGenerations(properties.staleAfter());
		if (count > 0) {
			log.warn("Marked {} stale reading generation(s) as failed", count);
		}
	}
}
