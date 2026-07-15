package com.myeongro.api.domain.reading.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.dto.ReadingResult;
import com.myeongro.api.domain.reading.dto.ReadingSection;
import com.myeongro.api.domain.reading.entity.ReadingKind;
import com.myeongro.api.domain.reading.entity.TarotSpreadType;

@Component
public class DemoReadingGenerator implements ReadingGenerator {

	@Override
	public ReadingResult generate(
		ReadingKind kind,
		TarotSpreadType spreadType,
		String question,
		Map<String, Object> input
	) {
		if (kind == ReadingKind.TAROT) {
			return tarot(question);
		}
		return saju(question);
	}

	private ReadingResult tarot(String question) {
		return new ReadingResult(
			"\uD0C0\uB85C \uB370\uBAA8 \uB9AC\uB529",
			"\uC138 \uC7A5\uC758 \uCE74\uB4DC\uAC00 \uD604\uC7AC \uD750\uB984\uC744 \uBCF4\uC5EC\uC90D\uB2C8\uB2E4.",
			List.of(
				new ReadingSection(
					null,
					"\uD604\uC7AC\uC758 \uD750\uB984",
					"\uC9C8\uBB38\uACFC \uC120\uD0DD\uD55C \uCE74\uB4DC\uB97C \uBC14\uD0D5\uC73C\uB85C "
						+ "\uCC28\uBD84\uD788 \uD750\uB984\uC744 \uC0B4\uD3B4\uBD05\uB2C8\uB2E4: "
						+ question
				),
				new ReadingSection(
					null,
					"\uB2E4\uC74C \uC120\uD0DD",
					"\uD070 \uACB0\uB860\uBCF4\uB2E4 \uC624\uB298 \uBC14\uB85C "
						+ "\uC2E4\uD589\uD560 \uC218 \uC788\uB294 \uC791\uC740 \uD589\uB3D9\uC5D0 "
						+ "\uC9D1\uC911\uD574 \uBCF4\uC138\uC694."
				)
			),
			List.of(
				"\uC791\uC740 \uD589\uB3D9 \uD558\uB098\uB97C \uBA3C\uC800 \uC815\uD558\uC138\uC694.",
				"\uACB0\uC815\uC744 \uBBF8\uB8E8\uAE30\uBCF4\uB2E4 "
					+ "\uD655\uC778 \uAC00\uB2A5\uD55C \uC815\uBCF4\uBD80\uD130 \uC815\uB9AC\uD558\uC138\uC694."
			),
			"\uC774 \uB9AC\uB529\uC740 \uC624\uB77D\uACFC \uC790\uAE30\uC131\uCC30\uC744 "
				+ "\uC704\uD55C \uCC38\uACE0 \uC790\uB8CC\uC785\uB2C8\uB2E4."
		);
	}

	private ReadingResult saju(String question) {
		return new ReadingResult(
			"\uC0AC\uC8FC \uB370\uBAA8 \uB9AC\uB529",
			"\uC785\uB825\uD55C \uC0DD\uB144\uC6D4\uC77C\uC744 \uBC14\uD0D5\uC73C\uB85C "
				+ "\uD604\uC7AC\uC758 \uD750\uB984\uC744 \uAC04\uB2E8\uD788 \uC815\uB9AC\uD569\uB2C8\uB2E4.",
			List.of(new ReadingSection(
				null,
				"\uC624\uB298\uC758 \uADE0\uD615",
				"\uC9C8\uBB38\uC744 \uAE30\uC900\uC73C\uB85C \uBB34\uB9AC\uD558\uC9C0 "
					+ "\uC54A\uB294 \uC120\uD0DD\uACFC \uD68C\uBCF5\uC758 \uB9AC\uB4EC\uC744 "
					+ "\uC0B4\uD3B4\uBCF4\uC138\uC694: "
					+ question
			)),
			List.of(
				"\uAC00\uC7A5 \uBD80\uB2F4\uC774 \uC801\uC740 \uC120\uD0DD\uC9C0\uBD80\uD130 "
					+ "\uC2E4\uD589\uD574 \uBCF4\uC138\uC694."
			),
			"\uC774 \uB9AC\uB529\uC740 \uC624\uB77D\uACFC \uC790\uAE30\uC131\uCC30\uC744 "
				+ "\uC704\uD55C \uCC38\uACE0 \uC790\uB8CC\uC785\uB2C8\uB2E4."
		);
	}
}
