package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

class SajuM3VerificationTests {
	@Test void measuresSyntheticSequentialAndFourWorkerCalculationAndExportsPublicCalendar() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		var corrector = new TrueSolarTimeCorrector();
		var service = new SajuCalculationService(new SajuBirthPlaceCatalog(mapper,
			new ClassPathResource("saju/birth-places/kr-admin-v1.json")), corrector,
			new ApproximateBirthTimeResolver(corrector, new LunarJavaFourPillarsAdapter()));
		var monthly = new SajuMonthlyCalendarService();
		Map<String, Object> measurements = new LinkedHashMap<>();
		for (String precision : List.of("exact", "approximate", "unknown")) {
			Map<String, Object> profile = new LinkedHashMap<>(Map.of(
				"birthDate", "1992-08-17", "birthTimePrecision", precision, "luckDirectionBasis", "unspecified"));
			if (!precision.equals("unknown")) { profile.put("birthTime", "12:00"); profile.put("provinceCode", "11"); }
			Callable<Long> measure = () -> {
				long start = System.nanoTime();
				var snapshot = service.calculate(profile, 2027);
				assertThat(monthly.calculate(2027, snapshot.dayMaster()).months()).hasSize(12);
				return (System.nanoTime() - start) / 1_000_000;
			};
			measure.call(); // Warm-up: no real user data or network.
			List<Long> sequential = new ArrayList<>();
			for (int i = 0; i < 6; i++) sequential.add(measure.call());
			List<Long> concurrent = new ArrayList<>();
			try (var executor = Executors.newFixedThreadPool(4)) {
				List<Callable<Long>> tasks = new ArrayList<>();
				for (int i = 0; i < 8; i++) tasks.add(measure);
				for (var result : executor.invokeAll(tasks)) concurrent.add(result.get());
			}
			measurements.put(precision, Map.of("sequentialMs", sequential, "fourWorkerMs", concurrent));
		}
		Files.createDirectories(Path.of("build", "saju-m3"));
		mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("build", "saju-m3", "performance.json").toFile(), measurements);
		mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("build", "saju-m3", "calendar-2027.json").toFile(), monthly.calculate(2027, "임"));
		int nextYear = java.time.LocalDate.now(SajuCalculationRules.BIRTH_ZONE).getYear() + 1;
		mapper.writerWithDefaultPrettyPrinter().writeValue(Path.of("build", "saju-m3", "calendar-next-year.json").toFile(), monthly.calculate(nextYear, "임"));
	}
}
