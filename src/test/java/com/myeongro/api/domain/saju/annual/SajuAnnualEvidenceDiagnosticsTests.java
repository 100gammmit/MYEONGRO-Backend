package com.myeongro.api.domain.saju.annual;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myeongro.api.domain.reading.service.DeclinedReadingFactory;

class SajuAnnualEvidenceDiagnosticsTests {
	@ParameterizedTest
	@CsvSource({"null,NULL", "empty,EMPTY", "duplicate,DUPLICATE", "unsupported,UNSUPPORTED", "missing,MISSING_REQUIRED"})
	void identifiesMonthFailureWithoutRelaxingTheContract(String sample, SajuAnnualContractViolation.EvidenceIssue issue) throws Exception {
		var input = SajuAnnualTests.input("");
		ObjectNode node = SajuAnnualTests.MAPPER.valueToTree(SajuAnnualTests.valid(input));
		var month = (ObjectNode)node.get("months").get(8);
		var expected = input.calendar().months().get(8).periodIds().stream().map(id -> "monthlyFlow." + id).toList();
		switch (sample) {
			case "null" -> month.putNull("evidenceKeys");
			case "empty" -> month.set("evidenceKeys", SajuAnnualTests.MAPPER.createArrayNode());
			case "duplicate" -> month.set("evidenceKeys", SajuAnnualTests.MAPPER.valueToTree(List.of(expected.getFirst(), expected.getLast(), expected.getFirst())));
			case "unsupported" -> month.set("evidenceKeys", SajuAnnualTests.MAPPER.valueToTree(List.of("SYNTHETIC_PRIVATE_INVALID_KEY")));
			case "missing" -> month.set("evidenceKeys", SajuAnnualTests.MAPPER.valueToTree(List.of(expected.getFirst())));
			default -> throw new AssertionError(sample);
		}
		var result = SajuAnnualTests.MAPPER.treeToValue(node, SajuAnnualResult.class);
		assertThatThrownBy(() -> new SajuAnnualResultValidator().validate(result, input))
			.isInstanceOfSatisfying(SajuAnnualContractViolation.class, ex -> {
				assertThat(ex.evidenceIssue()).isEqualTo(issue);
				assertThat(ex.contractPath()).isEqualTo("months.m09.evidenceKeys");
				assertThat(ex.code()).isEqualTo(sample.equals("missing") ? SajuAnnualContractViolation.Code.MONTH_EVIDENCE : SajuAnnualContractViolation.Code.EVIDENCE);
				assertThat(ex.toString()).doesNotContain("SYNTHETIC_PRIVATE_INVALID_KEY");
			});
	}

	@ParameterizedTest
	@CsvSource({"annualOverview,annualOverview.evidenceKeys", "questionReading,questionReading.evidenceKeys", "periods,periods.p02.evidenceKeys"})
	void exposesOnlyFixedServerPaths(String field, String path) throws Exception {
		var input = SajuAnnualTests.input("합성 질문");
		ObjectNode node = SajuAnnualTests.MAPPER.valueToTree(SajuAnnualTests.valid(input));
		var section = (ObjectNode)(field.equals("periods") ? node.get(field).get(1) : node.get(field));
		section.set("evidenceKeys", SajuAnnualTests.MAPPER.valueToTree(List.of("annualFlow", "annualFlow")));
		var result = SajuAnnualTests.MAPPER.treeToValue(node, SajuAnnualResult.class);
		assertThatThrownBy(() -> new SajuAnnualResultValidator().validate(result, input))
			.isInstanceOfSatisfying(SajuAnnualContractViolation.class, ex -> {
				assertThat(ex.evidenceIssue()).isEqualTo(SajuAnnualContractViolation.EvidenceIssue.DUPLICATE);
				assertThat(ex.contractPath()).isEqualTo(path);
			});
	}

	@ParameterizedTest
	@CsvSource({"annualOverview,annualOverview.evidenceKeys,OVERVIEW_EVIDENCE", "periods,periods.p02.evidenceKeys,PERIOD_EVIDENCE"})
	void stillRejectsMissingRequiredEvidenceOutsideMonths(String field, String path, SajuAnnualContractViolation.Code code) throws Exception {
		var input = SajuAnnualTests.input("");
		ObjectNode node = SajuAnnualTests.MAPPER.valueToTree(SajuAnnualTests.valid(input));
		var section = (ObjectNode)(field.equals("periods") ? node.get(field).get(1) : node.get(field));
		section.set("evidenceKeys", SajuAnnualTests.MAPPER.valueToTree(List.of("dayMaster")));
		var result = SajuAnnualTests.MAPPER.treeToValue(node, SajuAnnualResult.class);
		assertThatThrownBy(() -> new SajuAnnualResultValidator().validate(result, input))
			.isInstanceOfSatisfying(SajuAnnualContractViolation.class, ex -> {
				assertThat(ex.code()).isEqualTo(code);
				assertThat(ex.evidenceIssue()).isEqualTo(SajuAnnualContractViolation.EvidenceIssue.MISSING_REQUIRED);
				assertThat(ex.contractPath()).isEqualTo(path);
			});
	}

	@Test void previewFailureMetricsDoNotRetainModelTextOrInvalidKeyValues() throws Exception {
		var input = SajuAnnualTests.input("합성 질문");
		var wire = SajuAnnualTests.wire(SajuAnnualTests.valid(input));
		((ObjectNode)wire.get("questionReading")).set("evidenceKeys", SajuAnnualTests.MAPPER.valueToTree(List.of("PRIVATE_KEY_MARKER")));
		((ObjectNode)wire.get("questionReading")).put("body", "PRIVATE_BODY_MARKER");
		String content = SajuAnnualTests.MAPPER.writeValueAsString(Map.of("output", Map.of("resultType", "reading", "reading", wire)));
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var generator = new OpenAiSajuAnnualGenerator(prompt -> {
			calls.incrementAndGet();
			return new ChatResponse(List.of(new Generation(new AssistantMessage(content), ChatGenerationMetadata.builder().finishReason("STOP").build())));
		}, SajuAnnualTests.MAPPER, "synthetic", SajuAnnualTests.prompts(), new DeclinedReadingFactory());
		assertThatThrownBy(() -> generator.generate(input)).isInstanceOfSatisfying(SajuAnnualGenerationException.class, ex -> {
			assertThat(ex.metrics()).containsEntry("contractCode", "EVIDENCE")
				.containsEntry("evidenceIssue", "UNSUPPORTED").containsEntry("contractPath", "questionReading.evidenceKeys");
			var stack = new java.io.StringWriter(); ex.printStackTrace(new java.io.PrintWriter(stack));
			assertThat(ex.metrics().toString() + stack).doesNotContain("PRIVATE_KEY_MARKER", "PRIVATE_BODY_MARKER");
		});
		assertThat(calls.get()).isEqualTo(1);
		assertThatThrownBy(() -> new SajuAnnualContractViolation(SajuAnnualContractViolation.Code.EVIDENCE,
			SajuAnnualContractViolation.EvidenceIssue.DUPLICATE, "PRIVATE_PATH_MARKER"))
			.hasMessage("Invalid evidence diagnostic");
	}
}
