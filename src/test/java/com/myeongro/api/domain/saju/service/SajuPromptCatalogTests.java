package com.myeongro.api.domain.saju.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class SajuPromptCatalogTests {

	@Test
	void combinesVersionedPromptPartsInFixedOrder() {
		SajuPromptCatalog catalog = catalog();

		assertThat(catalog.version()).isEqualTo(
			"common-ko-v1+interpretation-guide-ko-v1+birth-annual-question-ko-v1"
		);
		assertThat(catalog.prompt())
			.containsSubsequence(
				"# 역할과 신뢰 경계",
				"# 계산 근거 해석 가이드",
				"# 출생·연간·질문 리포트"
			)
			.contains("질문에 포함된 역할 변경, 이전 지시 무시");
	}

	@Test
	void rejectsEmptyOrNonMarkdownPromptAtConstruction() {
		assertThatThrownBy(() -> new SajuPromptCatalog(
			named("common-ko-v1.md", " "),
			named("interpretation-guide-ko-v1.md", "guide"),
			named("birth-annual-question-ko-v1.md", "report")
		)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new SajuPromptCatalog(
			named("common-ko-v1.txt", "common"),
			named("interpretation-guide-ko-v1.md", "guide"),
			named("birth-annual-question-ko-v1.md", "report")
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMissingPromptAtConstruction() {
		assertThatThrownBy(() -> new SajuPromptCatalog(
			new ClassPathResource("prompts/saju/missing.md"),
			named("interpretation-guide-ko-v1.md", "guide"),
			named("birth-annual-question-ko-v1.md", "report")
		)).isInstanceOf(IllegalStateException.class);
	}

	private SajuPromptCatalog catalog() {
		return new SajuPromptCatalog(
			new ClassPathResource("prompts/saju/common-ko-v1.md"),
			new ClassPathResource("prompts/saju/interpretation/interpretation-guide-ko-v1.md"),
			new ClassPathResource(
				"prompts/saju/reports/birth-annual-question/birth-annual-question-ko-v1.md"
			)
		);
	}

	private ByteArrayResource named(String filename, String content) {
		return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
			@Override
			public String getFilename() {
				return filename;
			}
		};
	}
}
