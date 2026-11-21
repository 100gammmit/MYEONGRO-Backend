package com.myeongro.api.domain.saju.annual;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import com.myeongro.api.domain.reading.service.DeclinedReadingFactory;
import com.myeongro.api.domain.saju.calculation.SajuMonthlyCalendarService;

class SajuAnnualCalendarDaysTests {
	@ParameterizedTest
	@ValueSource(ints = {2026, 2027, 2028, 2029})
	void measuresEachMonthSliceIncludingLeapFebruary(int year) {
		var source = new SajuMonthlyCalendarService().calculate(year, "을");
		var calendar = SajuAnnualCalendar.from(source);
		for (int index = 0; index < 12; index++) {
			var month = calendar.months().get(index);
			assertThat(month.periodDays()).hasSameSizeAs(month.periodIds());
			for (int sliceIndex = 0; sliceIndex < 2; sliceIndex++) {
				var slice = source.months().get(index).periods().get(sliceIndex);
				var start = OffsetDateTime.parse(slice.startInclusive());
				var end = OffsetDateTime.parse(slice.endExclusive());
				var expected = BigDecimal.valueOf(Duration.between(start, end).toSeconds())
					.divide(BigDecimal.valueOf(86_400), 1, RoundingMode.HALF_UP);
				assertThat(month.periodDays().get(sliceIndex)).isEqualByComparingTo(expected);
			}
			var total = month.periodDays().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
			var longest = month.periodDays().stream().max(BigDecimal::compareTo).orElseThrow();
			assertThat(OpenAiSajuAnnualGenerator.modelMonth(month).primaryPeriodId())
				.isEqualTo(month.periodIds().get(month.periodDays().indexOf(longest)));
			assertThat(total).isCloseTo(BigDecimal.valueOf(YearMonth.of(year, index + 1).lengthOfMonth()),
				within(new BigDecimal("0.1")));
		}
		assertThat(calendar.months().get(1).periodDays().getFirst().scale()).isEqualTo(1);
	}

	@Test void clipsSharedPeriodToEachMonthRatherThanUsingItsMergedLength() {
		var calendar = SajuAnnualCalendar.from(new SajuMonthlyCalendarService().calculate(2027, "을"));
		var january = calendar.months().getFirst();
		var february = calendar.months().get(1);
		assertThat(january.periodIds().getLast()).isEqualTo(february.periodIds().getFirst());
		assertThat(january.periodDays()).containsExactly(new BigDecimal("5.0"), new BigDecimal("26.0"));
		assertThat(february.periodDays()).containsExactly(new BigDecimal("3.4"), new BigDecimal("24.6"));
		assertThat(calendar.months().getLast().periodDays()).containsExactly(new BigDecimal("6.7"), new BigDecimal("24.3"));
		assertThatThrownBy(() -> january.periodDays().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> new SajuAnnualCalendar.Month(1, List.of("p01"), List.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@ParameterizedTest @ValueSource(booleans = {false, true})
	void sendsNumericDaysAndPrimaryIdInTheExistingSingleProviderRequest(boolean brief) throws Exception {
		var input = brief ? SajuAnnualInput.from("", "self", 2027,
			SajuAnnualTests.snapshot("unknown", null, "1992-08-17"), SajuAnnualTests.MAPPER) : SajuAnnualTests.input("");
		String content = SajuAnnualTests.MAPPER.writeValueAsString(Map.of("output", Map.of(
			"resultType", "reading", "reading", SajuAnnualTests.wire(SajuAnnualTests.valid(input)))));
		var captured = new AtomicReference<Prompt>();
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var generator = new OpenAiSajuAnnualGenerator(prompt -> {
			calls.incrementAndGet(); captured.set(prompt);
			return new ChatResponse(List.of(new Generation(new AssistantMessage(content),
				ChatGenerationMetadata.builder().finishReason("STOP").build())));
		}, SajuAnnualTests.MAPPER, "synthetic-model", SajuAnnualTests.prompts(), new DeclinedReadingFactory());
		var result = generator.generate(input);
		var text = captured.get().getUserMessage().getText();
		var months = SajuAnnualTests.MAPPER.readTree(text).required("calendarMonths");
		assertThat(months.size()).isEqualTo(12);
		for (int index = 0; index < 12; index++) {
			var month = months.get(index);
			var fieldNames = new java.util.ArrayList<String>(); month.fieldNames().forEachRemaining(fieldNames::add);
			assertThat(fieldNames).containsExactly("month", "periodIds", "periodDays", "primaryPeriodId");
			assertThat(month.required("periodDays").size()).isEqualTo(2);
			var source = input.calendar().months().get(index);
			int primary = source.periodDays().getFirst().compareTo(source.periodDays().getLast()) > 0 ? 0 : 1;
			assertThat(month.required("primaryPeriodId").asText()).isEqualTo(source.periodIds().get(primary));
			for (int slice = 0; slice < 2; slice++) {
				assertThat(month.required("periodDays").get(slice).isNumber()).isTrue();
				assertThat(month.required("periodDays").get(slice).decimalValue())
					.isEqualByComparingTo(input.calendar().months().get(index).periodDays().get(slice));
				assertThat(month.required("periodIds").get(slice).asText())
					.isEqualTo(input.calendar().months().get(index).periodIds().get(slice));
			}
		}
		assertThat(calls.get()).isEqualTo(1);
		assertThat(text).doesNotContain("birthDate", "birthTime", "provinceCode", "longitude", "startInclusive", "endExclusive");
		assertThat(result.reading().payload()).doesNotContainKey("calendarMonths");
		assertThat(result.reading().payload().toString()).doesNotContain("periodDays");
		assertThat(result.reading().payload().toString()).doesNotContain("primaryPeriodId");
		assertThat(SajuAnnualTests.MAPPER.writeValueAsString(input.calendar())).doesNotContain("primaryPeriodId");
		assertThat(SajuAnnualTests.MAPPER.writeValueAsString(generator.schema(input))).doesNotContain("primaryPeriodId");
	}

	@Test void choosesByMonthlyLengthRatherThanIdOrArrayPosition() {
		var first = new SajuAnnualCalendar.Month(2, List.of("p09", "p02"), List.of(new BigDecimal("26.0"), new BigDecimal("5.0")));
		var last = new SajuAnnualCalendar.Month(2, List.of("p09", "p02"), List.of(new BigDecimal("5.0"), new BigDecimal("26.0")));
		assertThat(OpenAiSajuAnnualGenerator.modelMonth(first).primaryPeriodId()).isEqualTo("p09");
		assertThat(OpenAiSajuAnnualGenerator.modelMonth(last).primaryPeriodId()).isEqualTo("p02");
		assertThat(OpenAiSajuAnnualGenerator.modelMonth(first).periodIds()).isEqualTo(first.periodIds());
		assertThat(OpenAiSajuAnnualGenerator.modelMonth(first).periodDays()).isEqualTo(first.periodDays());
	}

	@Test void rejectsMissingNonpositiveAndTiedLengthsInsteadOfGuessing() {
		for (var days : List.of(List.<BigDecimal>of(), List.of(BigDecimal.ZERO, BigDecimal.ONE),
			List.of(new BigDecimal("-1"), BigDecimal.ONE), List.of(new BigDecimal("15.0"), new BigDecimal("15.00")))) {
			var ids = days.isEmpty() ? List.<String>of() : List.of("p01", "p02");
			assertThatThrownBy(() -> OpenAiSajuAnnualGenerator.modelMonth(new SajuAnnualCalendar.Month(1, ids, days)))
				.isInstanceOf(IllegalArgumentException.class);
		}
	}
}
