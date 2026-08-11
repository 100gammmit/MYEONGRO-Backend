package com.myeongro.api.domain.reading.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.myeongro.api.domain.reading.dto.GeneratedReading;

@Component
public class DeclinedReadingFactory {

	public GeneratedReading create(ReadingDeclineReason reason) {
		DeclineCopy copy = copy(reason);
		return new GeneratedReading(
			copy.title(),
			Map.of(
				"resultType", "declined",
				"reasonCode", reason.name(),
				"title", copy.title(),
				"message", copy.message(),
				"guidance", copy.guidance(),
				"disclaimer", copy.disclaimer()
			)
		);
	}

	private DeclineCopy copy(ReadingDeclineReason reason) {
		return switch (reason) {
			case CRISIS_OR_IMMEDIATE_DANGER -> new DeclineCopy(
				"지금은 리딩보다 즉각적인 도움이 먼저예요",
				"자신이나 다른 사람의 안전이 위태로울 수 있는 질문에는 타로·사주 해석을 제공하지 않아요.",
				List.of(
					"즉각적인 위험이 있다면 112 또는 119에 연락해 주세요.",
					"혼자 있지 말고 지금 연락할 수 있는 믿을 만한 사람에게 상황을 알려 주세요."
				),
				"이 안내는 전문적인 의료 또는 위기 상담을 대신하지 않습니다."
			);
			case MEDICAL_DECISION -> decisionCopy(
				"건강에 관한 중요한 결정은 리딩으로 답하기 어려워요",
				"진단, 치료, 수술 또는 복약 여부처럼 건강에 직접 영향을 주는 결정은 의료 전문가와 확인해 주세요.",
				"불안한 마음이나 결정을 앞두고 살펴보고 싶은 감정으로 질문을 바꾸면 리딩을 이어갈 수 있어요.",
				"이 안내와 리딩은 전문적인 의료 조언을 대신하지 않습니다."
			);
			case LEGAL_DECISION -> decisionCopy(
				"법적 판단이 필요한 질문은 리딩으로 결정해 드릴 수 없어요",
				"소송, 계약, 신고 또는 권리 행사처럼 법적 결과가 따르는 결정은 자격 있는 전문가와 확인해 주세요.",
				"이 상황에서 느끼는 부담이나 선택 전 살펴볼 마음으로 질문을 바꾸면 리딩을 이어갈 수 있어요.",
				"이 안내와 리딩은 전문적인 법률 조언을 대신하지 않습니다."
			);
			case FINANCIAL_DECISION -> decisionCopy(
				"큰 재정 결정을 리딩으로 정해 드리기는 어려워요",
				"투자, 대출, 보증 또는 큰 금액의 지출처럼 손실 위험이 큰 결정은 객관적인 정보와 전문가의 도움을 함께 확인해 주세요.",
				"결정을 앞두고 놓치고 있는 관점이나 자신의 심리를 묻는 방식으로 바꾸면 리딩을 이어갈 수 있어요.",
				"이 안내와 리딩은 전문적인 금융 조언을 대신하지 않습니다."
			);
			case HARMFUL_OR_ILLEGAL_ACTION -> new DeclineCopy(
				"이 질문에는 리딩을 제공할 수 없어요",
				"다른 사람을 해치거나 불법적인 행동을 실행·정당화하는 데 도움이 될 수 있는 질문에는 답하지 않아요.",
				List.of("누군가의 안전이 걱정되는 상황이라면 믿을 만한 사람이나 관련 기관에 도움을 요청해 주세요."),
				"타로와 사주는 오락과 자기 성찰을 위한 참고입니다."
			);
		};
	}

	private DeclineCopy decisionCopy(
		String title,
		String message,
		String guidance,
		String disclaimer
	) {
		return new DeclineCopy(title, message, List.of(guidance), disclaimer);
	}

	private record DeclineCopy(
		String title,
		String message,
		List<String> guidance,
		String disclaimer
	) {
	}
}
