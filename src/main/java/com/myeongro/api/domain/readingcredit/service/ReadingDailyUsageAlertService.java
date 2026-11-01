package com.myeongro.api.domain.readingcredit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.myeongro.api.domain.readingcredit.config.ReadingCreditProperties;
import com.myeongro.api.domain.readingcredit.repository.ReadingCreditRepository;

@Service
public class ReadingDailyUsageAlertService {

	private static final Logger log = LoggerFactory.getLogger(
		ReadingDailyUsageAlertService.class
	);

	private final ReadingCreditRepository repository;
	private final ReadingCreditProperties properties;

	public ReadingDailyUsageAlertService(
		ReadingCreditRepository repository,
		ReadingCreditProperties properties
	) {
		this.repository = repository;
		this.properties = properties;
	}

	@Scheduled(cron = "${app.reading-credits.cleanup-cron}")
	public void reportHighUsage() {
		int limit = properties.globalDailyAttemptLimit();
		// A limit of 0 is the operator's own emergency stop, so there is nothing to warn about.
		if (limit == 0) {
			return;
		}
		long percent = repository.todayAttemptCount() * 100L / limit;
		if (percent >= properties.usageAlertPercent()) {
			log.warn("READING_DAILY_USAGE_HIGH percent={}", percent);
		}
	}
}
