package com.myeongro.api.domain.reading.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

class ReadingRequestJsonTests {

	@Test
	void sortsNestedMapsIncludingArrayItemsWithoutChangingValuesOrArrayOrder() throws Exception {
		Map<String, Object> first = new LinkedHashMap<>();
		first.put("z", false);
		first.put("a", null);
		Map<String, Object> second = new LinkedHashMap<>();
		second.put("z", 2);
		second.put("a", "질문");
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("z", List.of(first, second));
		input.put("a", Map.of("b", 1, "a", List.of("second", "first")));
		ObjectMapper mapper = new ObjectMapper();
		String original = mapper.writeValueAsString(input);

		String json = ReadingRequestJson.write(mapper, input);

		assertThat(json).isEqualTo(
			"{\"a\":{\"a\":[\"second\",\"first\"],\"b\":1},"
				+ "\"z\":[{\"a\":null,\"z\":false},{\"a\":\"질문\",\"z\":2}]}"
		);
		assertThat(mapper.readTree(json)).isEqualTo(mapper.readTree(original));
		assertThat(mapper.writeValueAsString(input)).isEqualTo(original);
		assertThat(mapper.isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)).isFalse();
	}

	@Test
	void equivalentInsertionOrdersProduceExactlyTheSameText() throws Exception {
		Map<String, Object> forward = new LinkedHashMap<>();
		forward.put("a", Map.of("x", 1, "y", 2));
		forward.put("b", List.of("second", "first"));
		Map<String, Object> nested = new LinkedHashMap<>();
		nested.put("y", 2);
		nested.put("x", 1);
		Map<String, Object> reverse = new LinkedHashMap<>();
		reverse.put("b", List.of("second", "first"));
		reverse.put("a", nested);

		assertThat(ReadingRequestJson.write(new ObjectMapper(), reverse))
			.isEqualTo(ReadingRequestJson.write(new ObjectMapper(), forward));
	}
}
