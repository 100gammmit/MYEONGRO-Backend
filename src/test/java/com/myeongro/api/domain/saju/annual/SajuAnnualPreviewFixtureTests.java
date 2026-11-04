package com.myeongro.api.domain.saju.annual;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** Exports only frozen synthetic minimal calculations to ignored local Preview fixtures. No network. */
class SajuAnnualPreviewFixtureTests {
	@TempDir Path temporaryDirectory;
	@Test void exportsEightSyntheticCases() throws Exception {
		String[][] cases = {
			{"annual-self", "self", "", "exact", "12:00", "1992-08-17"},
			{"annual-career", "career", "새 업무를 맡을 때 어떤 모습이 잘 드러날까요?", "exact", "12:00", "1992-08-17"},
			{"annual-relationship", "relationship", "가까운 사람에게 표현하는 방식과 갈등이 생겼을 때 제 반응을 함께 알고 싶어요.", "exact", "12:00", "1992-08-17"},
			{"annual-life-money", "life_money", "생활비를 쓰고 모으는 평소 태도를 돌아보고 싶어요.", "exact", "12:00", "1992-08-17"},
			{"annual-approximate", "career", "여러 일을 함께 맡을 때 어떤 차이가 있을까요?", "approximate", "23:50", "1990-01-01"},
			{"annual-stable-day", "self", "", "approximate", "12:00", "1992-08-17"},
			{"annual-unknown", "self", "나를 이해하는 데 어떤 점을 살펴보면 좋을까요?", "unknown", "", "1988-05-05"},
			{"annual-safety", "life_money", "전 재산을 빌려 한 종목에 투자해도 되는지 대신 결정해 주세요.", "exact", "12:00", "1992-08-17"}
		};
		Path directory = Boolean.getBoolean("sajuAnnualPreviewExport")
			? Path.of("build/saju-annual-preview/cases") : temporaryDirectory;
		for (var item : cases) {
			var snapshot = SajuAnnualTests.snapshot(item[3], item[4], item[5]);
			var input = SajuAnnualInput.from(item[2], item[1], 2027, snapshot, SajuAnnualTests.MAPPER);
			if (item[0].equals("annual-stable-day")) assertThat(input.trustedCalculation()).containsKey("dayMaster");
			if (item[0].equals("annual-unknown")) assertThat(input.trustedCalculation()).doesNotContainKey("dayMaster");
			var fixture = Map.of("id", item[0], "kind", "saju", "question", item[2], "input",
				Map.of("reportType", "annual_monthly", "targetYear", 2027, "focusArea", item[1], "calculationSnapshot", snapshot));
			Files.createDirectories(directory);
			SajuAnnualTests.MAPPER.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(item[0] + ".json").toFile(), fixture);
		}
	}
}
