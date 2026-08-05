package com.myeongro.api.domain.saju.place;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

@Component
public class SajuBirthPlaceCatalog {

	private static final Pattern PROVINCE_CODE = Pattern.compile("\\d{2}");
	private static final Pattern CITY_CODE = Pattern.compile("\\d{5}");

	private final String version;
	private final List<ProvinceView> provinces;
	private final Map<String, SajuBirthPlace> placesByCityCode;

	public SajuBirthPlaceCatalog(
		ObjectMapper objectMapper,
		@Value("classpath:saju/birth-places/kr-admin-v1.json") Resource resource
	) {
		CatalogData data = read(objectMapper, resource);
		this.version = data.version();
		this.provinces = data.provinces();
		this.placesByCityCode = data.placesByCityCode();
	}

	public String version() {
		return version;
	}

	public List<ProvinceView> provinces() {
		return provinces;
	}

	public SajuBirthPlace require(String provinceCode, String cityCode) {
		SajuBirthPlace place = placesByCityCode.get(cityCode);
		if (place == null || !place.provinceCode().equals(provinceCode)) {
			throw new InvalidReadingRequestException(
				"INVALID_BIRTH_PLACE",
				"birthProfile.cityCode",
				"출생 도시를 다시 선택해 주세요."
			);
		}
		return place;
	}

	private CatalogData read(ObjectMapper objectMapper, Resource resource) {
		try {
			JsonNode root = objectMapper.readTree(resource.getInputStream());
			String catalogVersion = text(root, "version", "Birth-place catalog version is required");
			JsonNode provinceNodes = root.get("provinces");
			if (provinceNodes == null || !provinceNodes.isArray() || provinceNodes.isEmpty()) {
				throw new IllegalArgumentException("Birth-place provinces are required");
			}

			Set<String> provinceCodes = new HashSet<>();
			Set<String> cityCodes = new HashSet<>();
			List<ProvinceView> provinceViews = new ArrayList<>();
			Map<String, SajuBirthPlace> places = new LinkedHashMap<>();
			for (JsonNode provinceNode : provinceNodes) {
				String provinceCode = text(
					provinceNode, "provinceCode", "Province code is required"
				);
				String provinceName = text(
					provinceNode, "provinceName", "Province name is required"
				);
				if (!PROVINCE_CODE.matcher(provinceCode).matches()
					|| !provinceCodes.add(provinceCode)) {
					throw new IllegalArgumentException("Province code is invalid or duplicated");
				}
				JsonNode cityNodes = provinceNode.get("cities");
				if (cityNodes == null || !cityNodes.isArray() || cityNodes.isEmpty()) {
					throw new IllegalArgumentException("Birth-place cities are required");
				}
				List<CityView> cities = new ArrayList<>();
				for (JsonNode cityNode : cityNodes) {
					String cityCode = text(cityNode, "cityCode", "City code is required");
					String cityName = text(cityNode, "cityName", "City name is required");
					double latitude = number(cityNode, "latitude");
					double longitude = number(cityNode, "longitude");
					if (!CITY_CODE.matcher(cityCode).matches()
						|| !cityCode.startsWith(provinceCode)
						|| !cityCodes.add(cityCode)) {
						throw new IllegalArgumentException("City code is invalid or duplicated");
					}
					if (latitude < -90d || latitude > 90d
						|| longitude < -180d || longitude > 180d) {
						throw new IllegalArgumentException("Birth-place coordinate is invalid");
					}
					cities.add(new CityView(cityCode, cityName));
					places.put(cityCode, new SajuBirthPlace(
						provinceCode,
						provinceName,
						cityCode,
						cityName,
						latitude,
						longitude
					));
				}
				provinceViews.add(new ProvinceView(
					provinceCode,
					provinceName,
					List.copyOf(cities)
				));
			}
			return new CatalogData(
				catalogVersion,
				List.copyOf(provinceViews),
				Map.copyOf(places)
			);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot read birth-place catalog", exception);
		}
	}

	private String text(JsonNode node, String field, String errorMessage) {
		JsonNode value = node.get(field);
		if (value == null || !value.isTextual() || value.textValue().isBlank()) {
			throw new IllegalArgumentException(errorMessage);
		}
		return value.textValue();
	}

	private double number(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) {
			throw new IllegalArgumentException("Birth-place coordinate is required");
		}
		return value.doubleValue();
	}

	public record ProvinceView(
		String provinceCode,
		String provinceName,
		List<CityView> cities
	) {
	}

	public record CityView(String cityCode, String cityName) {
	}

	private record CatalogData(
		String version,
		List<ProvinceView> provinces,
		Map<String, SajuBirthPlace> placesByCityCode
	) {
	}
}
