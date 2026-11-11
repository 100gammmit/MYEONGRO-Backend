package com.myeongro.api.domain.saju.annual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import com.myeongro.api.domain.reading.service.ReadingSafetyPrompt;

class SajuAnnualPromptCatalogTests {

	@Test
	void composesSharedThenAnnualOnlyPartsAndIncludesEveryFilenameInVersion() {
		var catalog = catalog("annual-only material");
		assertThat(catalog.prompt()).isEqualTo(
			"shared common\n\nshared interpretation\n\nannual-only material\n\nannual report\n\nsafety"
		);
		assertThat(catalog.version()).isEqualTo(
			"shared-common+base-guide+monthly-guide+annual-report+safety"
		);
	}

	@Test
	void rejectsEmptyAnnualOnlyMaterial() {
		assertThatThrownBy(() -> catalog(" ")).isInstanceOf(IllegalArgumentException.class);
	}

	private SajuAnnualPromptCatalog catalog(String monthly) {
		return new SajuAnnualPromptCatalog(
			new ReadingSafetyPrompt(named("safety.md", "safety")),
			named("shared-common.md", "shared common"),
			named("base-guide.md", "shared interpretation"),
			named("monthly-guide.md", monthly),
			named("annual-report.md", "annual report")
		);
	}

	private ByteArrayResource named(String filename, String content) {
		return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
			@Override public String getFilename() { return filename; }
		};
	}
}
