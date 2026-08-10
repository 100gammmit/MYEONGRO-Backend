package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiPreviewReportWriterTests {

	@TempDir
	Path tempDir;

	@Test
	void writesJsonAndReadableMarkdownUnderLabelDirectory() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
		AiPreviewMarkdownRenderer markdownRenderer = new AiPreviewMarkdownRenderer(
			ZoneId.of("Asia/Seoul")
		);
		AiPreviewReportWriter writer = new AiPreviewReportWriter(
			objectMapper,
			markdownRenderer,
			tempDir
		);
		AiPreviewReport report = new AiPreviewReport(
			"before", "mind-basic", "tarot", "mind_three_card", "gpt-test",
			"common-v1+cards-v1+mind-v1", "abc123", Instant.parse("2026-08-10T00:00:00Z"),
			123L, Map.of("title", "테스트 제목", "summary", "테스트 요약")
		);

		AiPreviewWrittenReport output = writer.write(report);
		JsonNode json = objectMapper.readTree(output.json().toFile());
		String markdown = java.nio.file.Files.readString(output.markdown());

		assertThat(output.json()).isEqualTo(
			tempDir.resolve("before").resolve("tarot-mind-basic.json").toAbsolutePath()
		);
		assertThat(output.markdown()).isEqualTo(
			tempDir.resolve("before").resolve("tarot-mind-basic.md").toAbsolutePath()
		);
		assertThat(json.has("title")).isFalse();
		assertThat(json.path("promptSha256").asText()).isEqualTo("abc123");
		assertThat(json.path("result").path("summary").asText()).isEqualTo("테스트 요약");
		assertThat(markdown).contains("# 테스트 제목", "테스트 요약", "## 실행 정보");
	}
}
