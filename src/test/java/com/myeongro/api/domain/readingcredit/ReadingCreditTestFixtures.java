package com.myeongro.api.domain.readingcredit;

import java.time.Duration;

import com.myeongro.api.domain.readingcredit.config.ReadingCreditProperties;

public final class ReadingCreditTestFixtures {

	private ReadingCreditTestFixtures() {
	}

	public static ReadingCreditProperties properties() {
		return new ReadingCreditProperties(
			10,
			new ReadingCreditProperties.Costs(
				new ReadingCreditProperties.Tarot(1, 2, 2, 3),
				4
			),
			Duration.ofMinutes(5),
			"0 */5 * * * *"
		);
	}
}
