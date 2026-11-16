package com.myeongro.api.domain.saju.calculation;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myeongro.api.domain.saju.controller.SajuCalculationRequest;
import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;
import org.junit.jupiter.api.Test;

class ManseryeokInputTests {
	private ObjectNode input() throws Exception {
		return (ObjectNode) ManseryeokCalculationTests.MAPPER.readTree("""
			{"birthProfile":{"calendarType":"solar","birthDate":"2000-06-15","birthTime":"14:30",
			"birthTimePrecision":"exact","provinceCode":"11","luckDirectionBasis":"unspecified"}}
			""");
	}
	private SajuCalculationRequest.Validated validate(ObjectNode node) throws Exception {
		return ManseryeokCalculationTests.MAPPER.treeToValue(node,SajuCalculationRequest.class).validate(ManseryeokCalculationTests.CATALOG);
	}
	@Test void missingOptionsAndMissingOptionFieldsUseDefaultsButExplicitNullDoesNot() throws Exception {
		assertThat(validate(input()).options()).isEqualTo(SajuCalculationOptions.DEFAULT);
		var node=input(); node.putObject("options");
		assertThat(validate(node).options()).isEqualTo(SajuCalculationOptions.DEFAULT);
		for (String json : new String[]{"null","[]","true","\"true\"","{\"dayBoundary\":null}","{\"equationOfTime\":null}","{\"equationOfTime\":\"false\"}","{\"dayBoundary\":\"sect2\"}","{\"legacy\":true}"}) {
			var bad=input(); bad.set("options",ManseryeokCalculationTests.MAPPER.readTree(json));
			assertThatThrownBy(()->validate(bad)).isInstanceOf(InvalidReadingRequestException.class);
		}
	}
	@Test void scalarCoercionsAndUnsupportedInputsAreRejected() throws Exception {
		for (String[] pair:new String[][]{{"birthDate","2000-02-30"},{"birthDate","1899-12-31"},{"birthDate","2100-01-01"},{"birthTime","24:00"},{"birthTime","9:00"},{"calendarType","lunar"},{"provinceCode","invalid"},{"cityCode","invalid"},{"birthTimePrecision","invalid"},{"luckDirectionBasis","invalid"}}) {
			var node=input(); ((ObjectNode)node.get("birthProfile")).put(pair[0],pair[1]);
			assertThatThrownBy(()->validate(node)).isInstanceOf(InvalidReadingRequestException.class);
		}
		for (String key:new String[]{"birthDate","birthTime","provinceCode","luckDirectionBasis"}) {
			var node=input(); ((ObjectNode)node.get("birthProfile")).put(key,12);
			assertThatThrownBy(()->validate(node)).isInstanceOf(InvalidReadingRequestException.class);
		}
		var node=input(); node.put("question","not accepted");
		assertThatThrownBy(()->validate(node)).isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
		var nested=input(); ((ObjectNode)nested.get("birthProfile")).put("userId","not accepted");
		assertThatThrownBy(()->validate(nested)).isInstanceOf(InvalidReadingRequestException.class);
	}
	@Test void unknownRejectsEvenNullLocationOrTimeAndCityMustBelongToProvince() throws Exception {
		var node=input(); var birth=(ObjectNode)node.get("birthProfile"); birth.put("birthTimePrecision","unknown"); birth.remove("birthTime"); birth.remove("provinceCode");
		assertThat(validate(node).coordinateSource()).isEqualTo("reference");
		for (String key:new String[]{"birthTime","provinceCode","cityCode"}) {
			var bad=node.deepCopy(); ((ObjectNode)bad.get("birthProfile")).putNull(key);
			assertThatThrownBy(()->validate(bad)).isInstanceOf(InvalidReadingRequestException.class);
		}
		var city=ManseryeokCalculationTests.CATALOG.publicProvinces().stream().filter(p->!p.provinceCode().equals("11")).findFirst().orElseThrow().cities().getFirst().cityCode();
		var bad=input(); ((ObjectNode)bad.get("birthProfile")).put("cityCode",city);
		assertThatThrownBy(()->validate(bad)).isInstanceOf(InvalidReadingRequestException.class);
	}
}
