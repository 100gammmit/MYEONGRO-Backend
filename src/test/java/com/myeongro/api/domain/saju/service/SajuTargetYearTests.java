package com.myeongro.api.domain.saju.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.saju.controller.SajuReadingCreateRequest;
import com.myeongro.api.domain.saju.place.SajuBirthPlaceCatalog;

class SajuTargetYearTests {
	@Test void rejectsNonIntegerJsonYearsInsteadOfCoercingThem() throws Exception {
		var mapper = new ObjectMapper();
		for (String value : new String[]{"2026.5", "\"2026\"", "true", "2147483648"}) {
			assertThatThrownBy(() -> mapper.readValue("{\"targetYear\":" + value + "}", SajuReadingCreateRequest.class))
				.isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
		}
		assertThat(mapper.readValue("{\"targetYear\":2027}", SajuReadingCreateRequest.class).targetYear()).isEqualTo(2027);
	}
}
