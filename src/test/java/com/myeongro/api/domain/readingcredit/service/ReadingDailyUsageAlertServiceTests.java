package com.myeongro.api.domain.readingcredit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.myeongro.api.domain.readingcredit.config.ReadingCreditProperties;
import com.myeongro.api.domain.readingcredit.repository.ReadingCreditRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ReadingDailyUsageAlertServiceTests {

	private final ReadingCreditRepository repository = mock(ReadingCreditRepository.class);
	private final Logger logger = (Logger) LoggerFactory.getLogger(ReadingDailyUsageAlertService.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void captureLogs() {
		appender.start();
		logger.addAppender(appender);
	}

	@AfterEach
	void releaseLogs() {
		logger.detachAppender(appender);
	}

	private ReadingDailyUsageAlertService service(int limit) {
		return new ReadingDailyUsageAlertService(repository, new ReadingCreditProperties(
			10,
			new ReadingCreditProperties.Costs(new ReadingCreditProperties.Tarot(2, 2, 3), 4),
			Duration.ofMinutes(5),
			"0 */5 * * * *",
			limit,
			50
		));
	}

	@Test
	void staysQuietBelowTheAlertLine() {
		when(repository.todayAttemptCount()).thenReturn(799);

		service(1600).reportHighUsage();

		assertThat(appender.list).isEmpty();
	}

	@Test
	void warnsWithThePercentOnceTheAlertLineIsReached() {
		when(repository.todayAttemptCount()).thenReturn(800);

		service(1600).reportHighUsage();

		assertThat(appender.list).singleElement()
			.extracting(ILoggingEvent::getFormattedMessage)
			.isEqualTo("READING_DAILY_USAGE_HIGH percent=50");
	}

	@Test
	void skipsTheCheckDuringAnEmergencyStop() {
		service(0).reportHighUsage();

		assertThat(appender.list).isEmpty();
		verifyNoInteractions(repository);
	}
}
