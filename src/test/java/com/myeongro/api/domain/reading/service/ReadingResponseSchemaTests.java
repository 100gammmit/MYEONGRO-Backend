package com.myeongro.api.domain.reading.service;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ReadingResponseSchemaTests {

	@Test
	void preservesBranchAndDiscriminatorOrderAfterSerialization() throws Exception {
		Map<String, Object> reading = ReadingResponseSchema.orderedMap(entry("type", "object"));
		Map<String, Object> schema = ReadingResponseSchema.wrap(reading);
		var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(schema));
		assertThat(json.properties().stream().map(Map.Entry::getKey).toList())
			.containsExactly("additionalProperties", "properties", "required", "type");
		var branches = json.at("/properties/output/anyOf");
		assertThat(branches.get(0).get("properties").properties().stream().map(Map.Entry::getKey).toList())
			.containsExactly("reading", "resultType");
		assertThat(branches.get(1).get("properties").properties().stream().map(Map.Entry::getKey).toList())
			.containsExactly("reasonCode", "resultType");
		assertThat(branches.get(0).at("/properties/resultType/enum/0").asText()).isEqualTo("reading");
		assertThat(branches.get(1).at("/properties/resultType/enum/0").asText()).isEqualTo("declined");
		assertThat(branches.get(1).at("/properties/reasonCode/enum"))
			.isEqualTo(new ObjectMapper().valueToTree(ReadingDeclineReason.modelSelectableValues()));
	}

	@Test
	void orderedMapIsImmutableAndRejectsDuplicateKeys() {
		Map<String, Object> map = ReadingResponseSchema.orderedMap(entry("z", 1), entry("a", 2));
		assertThat(List.copyOf(map.keySet())).containsExactly("z", "a");
		assertThatThrownBy(() -> map.put("new", 3)).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> ReadingResponseSchema.orderedMap(entry("a", 1), entry("a", 2)))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
