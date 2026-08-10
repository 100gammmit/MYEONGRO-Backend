package com.myeongro.api.aipreview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

final class AiPreviewReportWriter {

	private final ObjectMapper objectMapper;
	private final AiPreviewMarkdownRenderer markdownRenderer;
	private final Path outputRoot;

	AiPreviewReportWriter(
		ObjectMapper objectMapper,
		AiPreviewMarkdownRenderer markdownRenderer,
		Path outputRoot
	) {
		this.objectMapper = objectMapper;
		this.markdownRenderer = markdownRenderer;
		this.outputRoot = outputRoot;
	}

	AiPreviewWrittenReport write(AiPreviewReport report) {
		Path directory = outputRoot.resolve(report.label());
		String basename = report.kind() + "-" + report.caseId();
		Path jsonOutput = directory.resolve(basename + ".json");
		Path markdownOutput = directory.resolve(basename + ".md");
		try {
			Files.createDirectories(directory);
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonOutput.toFile(), report);
			Files.writeString(
				markdownOutput,
				markdownRenderer.render(report),
				StandardCharsets.UTF_8
			);
			return new AiPreviewWrittenReport(
				jsonOutput.toAbsolutePath().normalize(),
				markdownOutput.toAbsolutePath().normalize()
			);
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot write AI preview result", exception);
		}
	}
}
