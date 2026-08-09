package com.myeongro.api.aipreview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

final class AiPreviewReportWriter {

	private final ObjectMapper objectMapper;
	private final Path outputRoot;

	AiPreviewReportWriter(ObjectMapper objectMapper, Path outputRoot) {
		this.objectMapper = objectMapper;
		this.outputRoot = outputRoot;
	}

	Path write(AiPreviewReport report) {
		Path directory = outputRoot.resolve(report.label());
		Path output = directory.resolve(report.kind() + "-" + report.caseId() + ".json");
		try {
			Files.createDirectories(directory);
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
			return output.toAbsolutePath().normalize();
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot write AI preview result", exception);
		}
	}
}
