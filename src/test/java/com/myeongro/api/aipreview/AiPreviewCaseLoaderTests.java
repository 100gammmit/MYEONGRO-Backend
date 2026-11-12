package com.myeongro.api.aipreview;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class AiPreviewCaseLoaderTests {
	private static final String MARKER = "SYNTHETIC_BIRTH_1988-05-05_12:00";

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void malformedJsonAndOuterBindingErrorsDoNotExposeBirthValues(boolean malformed, CapturedOutput output) {
		String input = malformed
			? "{\"birthProfile\":{\"birthDate\":" + MARKER + "}}"
			: "\"" + MARKER + "\"";
		String json = "{\"id\":\"invalid-saju\",\"kind\":\"saju\",\"question\":\"합성 질문\",\"input\":" + input + "}";
		ClassLoader original = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(new ClassLoader(original) {
			@Override public InputStream getResourceAsStream(String name) {
				if (name.equals("ai-preview/cases/saju/invalid-saju.json")) {
					return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
				}
				return super.getResourceAsStream(name);
			}
		});
		try {
			var loader = new AiPreviewCaseLoader(new ObjectMapper().findAndRegisterModules());
			assertThatThrownBy(() -> loader.load("saju", "invalid-saju"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Cannot load AI preview case")
				.hasNoCause()
				.satisfies(exception -> {
					exception.printStackTrace(System.out);
					exception.printStackTrace(System.err);
				});
			assertThat(output.getAll()).doesNotContain(MARKER, "1988-05-05", "12:00");
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}
}
