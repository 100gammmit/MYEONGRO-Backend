package com.myeongro.api.domain.reading.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

@Component
public class SensitiveReadingInputGuard {

	private static final List<Rule> RULES = List.of(
		new Rule(
			"IMMEDIATE_SAFETY_RISK",
			"위급하거나 즉각적인 도움이 필요한 내용은 리딩으로 다룰 수 없습니다. 긴급한 상황이라면 112 또는 119에 연락해 주세요.",
			Pattern.compile(
				"자살|죽고싶|죽어버리|목숨(?:을)?끊|극단적선택|자해|내몸(?:을)?해치|"
					+ "(?:사람|상대|그|그녀|가족|친구|동료|연인|누군가|타인|남편|아내|부모|아이|자식)(?:을|를).{0,6}(?:죽이|해치)|"
					+ "(?:나는|내가|저는|제가).{0,12}(?:죽이고싶|해치고싶)"
			)
		),
		new Rule(
			"HARMFUL_OR_ILLEGAL_REQUEST",
			"다른 사람에게 해를 주거나 불법 행위를 실행·은폐하는 내용은 리딩으로 다룰 수 없습니다.",
			Pattern.compile("살인방법|시체(?:를)?(?:숨|유기)|폭탄(?:을)?(?:만들|제조)|마약(?:을)?(?:만들|제조)|범죄(?:를)?(?:숨|은폐)|증거(?:를)?(?:없애|인멸)")
		),
		new Rule(
			"DIRECT_IDENTIFIER_NOT_ALLOWED",
			"연락처나 식별정보는 입력할 수 없습니다. 해당 정보를 지우고 다시 시도해 주세요.",
			Pattern.compile(
				"(?:[\\p{L}\\p{N}._%+\\-]+@[\\p{L}\\p{N}.\\-]+\\.[\\p{L}]{2,})"
					+ "|(?<!\\d)(?:(?:\\+82|0082)[- .]?(?:10|1[016789])|01[016789])[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)"
					+ "|(?<!\\d)0(?:2|3[1-3]|4[1-4]|5[1-5]|6[1-4]|70)[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)"
					+ "|(?<!\\d)(?:15|16|18)\\d{2}[- .]?\\d{4}(?!\\d)"
					+ "|(?<!\\d)\\d{6}[- ]?[1-8]\\d{6}(?!\\d)",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
			)
		),
		new Rule(
			"SENSITIVE_HEALTH_INFORMATION",
			"진단·병력·검사·복약·수술·임신과 같은 상세 건강정보는 입력할 수 없습니다. 일반적인 건강운이나 생활 흐름으로 바꿔 주세요.",
			Pattern.compile(
				"진단받|병력|검사결과|양성판정|음성판정|"
					+ "암(?:에)?(?:걸|진단|치료|수술|병기|환자|투병)|"
					+ "(?:당뇨|우울증|조울증|조현병|공황장애|에이즈|hiv|간염)(?:이|가|을|를|에|진단|병기|치료|환자)?|"
					+ "복약|투약|처방받|항우울제|수면제|피임약|인슐린|"
					+ "수술(?:했|받|예정|해야|할)|임신|유산|낙태|출산예정",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
			)
		),
		new Rule(
			"SENSITIVE_SEXUAL_INFORMATION",
			"성생활이나 성적 지향에 관한 상세정보는 입력할 수 없습니다. 관계의 감정이나 흐름을 묻는 표현으로 바꿔 주세요.",
			Pattern.compile("성관계|성생활|성적지향|동성애|양성애|무성애|게이|레즈비언|트랜스젠더")
		),
		new Rule(
			"SENSITIVE_BELIEF_INFORMATION",
			"정치적 견해나 종교·신념에 관한 상세정보는 입력할 수 없습니다. 개인을 식별하지 않는 일반적인 상황으로 바꿔 주세요.",
			Pattern.compile(
				"(?:나는|저는|제가).{0,12}(?:민주당|국민의힘|정당).{0,8}(?:가입|당원|지지(?:해|하|한|했)|후원)|"
					+ "(?:민주당|국민의힘|정당)(?:에)?(?:가입했|당원이|후원했)|"
					+ "(?:나는|저는|제가).{0,8}(?:기독교|천주교|불교|이슬람|무교)(?:야|입니다|신자|를믿|에다녀)|"
					+ "(?:기독교|천주교|불교|이슬람).{0,8}(?:신자|믿|개종|입교|탈교)"
			)
		)
	);

	public void validate(NormalizedReadingInput input) {
		for (FreeTextField field : freeTextFields(input)) {
			String normalized = normalize(field.value());
			String compact = normalized.replaceAll("[^\\p{L}\\p{N}@._%+\\-]", "");
			for (Rule rule : RULES) {
				if (rule.pattern().matcher(normalized).find()
					|| rule.pattern().matcher(compact).find()) {
					throw new InvalidReadingRequestException(
						rule.code(), field.name(), rule.message()
					);
				}
			}
		}
	}

	private List<FreeTextField> freeTextFields(NormalizedReadingInput input) {
		List<FreeTextField> fields = new ArrayList<>();
		fields.add(new FreeTextField("question", input.question()));
		Object choiceOptions = input.payload().get("choiceOptions");
		if (choiceOptions instanceof Map<?, ?> choices) {
			addText(fields, "choiceOptions.a", choices.get("a"));
			addText(fields, "choiceOptions.b", choices.get("b"));
		}
		return fields;
	}

	private void addText(List<FreeTextField> fields, String name, Object value) {
		if (value instanceof String text && !text.isBlank()) {
			fields.add(new FreeTextField(name, text));
		}
	}

	private String normalize(String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
	}

	private record Rule(String code, String message, Pattern pattern) {
	}

	private record FreeTextField(String name, String value) {
	}
}
