package com.myeongro.api.domain.reading.service;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.exception.InvalidReadingRequestException;

@Component
public class DirectIdentifierInputGuard {

	private static final String CODE = "DIRECT_IDENTIFIER_NOT_ALLOWED";
	private static final String MESSAGE =
		"이메일·전화번호·주민등록번호·카드번호처럼 형식이 확인되는 개인정보는 입력할 수 없습니다. 해당 정보를 지우고 다시 시도해 주세요.";
	private static final Pattern EMAIL = Pattern.compile(
		"[\\p{L}\\p{N}._%+\\-]+@[\\p{L}\\p{N}.\\-]+\\.[\\p{L}]{2,}",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern PHONE = Pattern.compile(
		"(?<!\\d)(?:\\+82|0082)[- .]?(?:(?:10|1[016789])[- .]?\\d{3,4}[- .]?\\d{4}|(?:2|3[1-3]|4[1-4]|5[1-5]|6[1-4]|70)[- .]?\\d{3,4}[- .]?\\d{4})(?!\\d)"
			+ "|(?<!\\d)01[016789][- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)"
			+ "|(?<!\\d)0(?:2|3[1-3]|4[1-4]|5[1-5]|6[1-4]|70)[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)"
			+ "|(?<!\\d)(?:15|16|18)\\d{2}[- .]?\\d{4}(?!\\d)"
	);
	private static final Pattern RESIDENT_NUMBER = Pattern.compile(
		"(?<!\\d)(\\d{6})[- ]?([1-4]\\d{6})(?!\\d)"
	);
	private static final Pattern CARD_NUMBER = Pattern.compile(
		"(?<!\\d)((?:\\d[ -]?){12,18}\\d)(?!\\d)"
	);
	private static final int[] RESIDENT_NUMBER_WEIGHTS = {
		2, 3, 4, 5, 6, 7, 8, 9, 2, 3, 4, 5
	};

	public void validate(ReadingRequestInput input) {
		for (FreeTextField field : freeTextFields(input)) {
			String normalized = Normalizer.normalize(field.value(), Normalizer.Form.NFKC);
			if (containsDirectIdentifier(normalized)) {
				throw new InvalidReadingRequestException(CODE, field.name(), MESSAGE);
			}
		}
	}

	private boolean containsDirectIdentifier(String value) {
		return EMAIL.matcher(value).find()
			|| PHONE.matcher(value).find()
			|| containsValidResidentNumber(value)
			|| containsLuhnCardNumber(value);
	}

	private boolean containsValidResidentNumber(String value) {
		Matcher matcher = RESIDENT_NUMBER.matcher(value);
		while (matcher.find()) {
			String digits = matcher.group(1) + matcher.group(2);
			if (hasValidResidentBirthDate(digits) && hasValidResidentChecksum(digits)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasValidResidentBirthDate(String digits) {
		int century = digits.charAt(6) == '1' || digits.charAt(6) == '2' ? 1900 : 2000;
		try {
			LocalDate.of(
				century + Integer.parseInt(digits.substring(0, 2)),
				Integer.parseInt(digits.substring(2, 4)),
				Integer.parseInt(digits.substring(4, 6))
			);
			return true;
		} catch (DateTimeException exception) {
			return false;
		}
	}

	private boolean hasValidResidentChecksum(String digits) {
		int sum = 0;
		for (int index = 0; index < RESIDENT_NUMBER_WEIGHTS.length; index++) {
			sum += Character.digit(digits.charAt(index), 10) * RESIDENT_NUMBER_WEIGHTS[index];
		}
		return (11 - (sum % 11)) % 10 == Character.digit(digits.charAt(12), 10);
	}

	private boolean containsLuhnCardNumber(String value) {
		Matcher matcher = CARD_NUMBER.matcher(value);
		while (matcher.find()) {
			String digits = matcher.group(1).replaceAll("\\D", "");
			if (digits.length() >= 13 && digits.length() <= 19 && passesLuhn(digits)) {
				return true;
			}
		}
		return false;
	}

	private boolean passesLuhn(String digits) {
		int sum = 0;
		boolean doubleDigit = false;
		for (int index = digits.length() - 1; index >= 0; index--) {
			int digit = Character.digit(digits.charAt(index), 10);
			if (doubleDigit) {
				digit *= 2;
				if (digit > 9) digit -= 9;
			}
			sum += digit;
			doubleDigit = !doubleDigit;
		}
		return sum % 10 == 0;
	}

	private List<FreeTextField> freeTextFields(ReadingRequestInput input) {
		List<FreeTextField> fields = new ArrayList<>();
		fields.add(new FreeTextField("question", input.question()));
		Object choiceOptions = input.validationPayload().get("choiceOptions");
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

	private record FreeTextField(String name, String value) {
	}
}
