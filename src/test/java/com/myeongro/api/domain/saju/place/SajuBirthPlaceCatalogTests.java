package com.myeongro.api.domain.saju.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

class SajuBirthPlaceCatalogTests {

	@Test
	void exposesProvinceOnlyPlacesWithInternalRepresentativeCoordinates() {
		SajuBirthPlaceCatalog catalog = catalog();

		assertThat(catalog.version()).isEqualTo("kr-admin-v1-province");
		assertThat(catalog.provinces()).hasSize(16);
		assertThat(catalog.provinces())
			.anySatisfy(province -> {
				assertThat(province.provinceCode()).isEqualTo("36");
				assertThat(province.provinceName()).isEqualTo("세종특별자치시");
			});

		SajuBirthPlace seoul = catalog.requireProvince("11");
		assertThat(seoul.provinceName()).isEqualTo("서울특별시");
		assertThat(seoul.latitude()).isBetween(33d, 39d);
		assertThat(seoul.longitude()).isBetween(124d, 132d);
	}

	@Test
	void rejectsMismatchedProvinceAndCityCodes() {
		assertThatThrownBy(() -> catalog().require("26", "11680"))
			.isInstanceOfSatisfying(InvalidReadingRequestException.class, exception -> {
				assertThat(exception.getCode()).isEqualTo("INVALID_BIRTH_PLACE");
				assertThat(exception.getField()).isEqualTo("birthProfile.cityCode");
			});
	}

	@Test
	void failsFastOnDuplicateOrInvalidCatalogEntries() {
		String duplicate = """
			{"version":"test-v1","provinces":[
			  {"provinceCode":"11","provinceName":"서울특별시","cities":[
			    {"cityCode":"11110","cityName":"종로구","latitude":37.5,"longitude":127.0},
			    {"cityCode":"11110","cityName":"중복","latitude":37.5,"longitude":127.0}
			  ]}
			]}
			""";

		assertThatThrownBy(() -> new SajuBirthPlaceCatalog(
			new ObjectMapper(),
			new ByteArrayResource(duplicate.getBytes(StandardCharsets.UTF_8))
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("City code");
	}

	private SajuBirthPlaceCatalog catalog() {
		return new SajuBirthPlaceCatalog(
			new ObjectMapper(),
			new ClassPathResource("saju/birth-places/kr-admin-v1.json")
		);
	}
}
