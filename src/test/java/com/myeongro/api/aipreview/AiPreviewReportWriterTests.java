package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiPreviewReportWriterTests {

	@TempDir
	Path tempDir;

	@Test
	void writesPrettyJsonUnderLabelDirectory() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
		AiPreviewReportWriter writer = new AiPreviewReportWriter(objectMapper, tempDir);
		AiPreviewReport report = new AiPreviewReport(
			"before", "mind-basic", "tarot", "mind_three_card", "gpt-test",
			"common-v1+cards-v1+mind-v1", "abc123", Instant.parse("2026-08-10T00:00:00Z"),
			123L, "테스트 제목", Map.of("summary", "테스트 요약")
		);

		Path output = writer.write(report);
		JsonNode json = objectMapper.readTree(output.toFile());

		assertThat(output).isEqualTo(
			tempDir.resolve("before").resolve("tarot-mind-basic.json").toAbsolutePath()
		);
		assertThat(json.path("promptSha256").asText()).isEqualTo("abc123");
		assertThat(json.path("result").path("summary").asText()).isEqualTo("테스트 요약");
	}
}
