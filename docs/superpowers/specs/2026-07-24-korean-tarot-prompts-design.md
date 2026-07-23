# 활성 타로 프롬프트 한국어 전환 설계

## 목표

현재 운영에서 사용하는 타로 프롬프트의 영문 지시문을 자연스러운 한국어로 바꾼다. 기존 리딩의 말투, 안전 원칙, 스프레드별 해석 목적 및 구조화 출력 계약은 변경하지 않는다.

## 변경 범위

다음 활성 프롬프트 6개만 수정한다.

- `prompts/tarot/common-ko-v1.md`
- `prompts/tarot/major-arcana-ko-v1.md`
- `prompts/tarot/spreads/daily-one-card-ko-v2.md`
- `prompts/tarot/spreads/mind-three-card-ko-v2.md`
- `prompts/tarot/spreads/relationship-three-card-ko-v2.md`
- `prompts/tarot/spreads/choice-five-card-ko-v2.md`

운영 설정에서 참조하지 않는 스프레드 v1 파일 4개는 수정하지 않는다.

## 번역 원칙

- 영문 문장 구조를 그대로 옮기지 않고 한국어 지시문으로 자연스럽게 다듬는다.
- 따뜻하고 차분하며 현실적인 자기성찰 리딩이라는 현재 방향을 유지한다.
- 예언, 운명론, 공포 조장, 타인의 숨은 생각 단정 및 전문 영역 지시를 제한하는 안전 규칙을 보존한다.
- 각 스프레드의 섹션 목적, 순서 및 가이던스 개수 조건을 보존한다.
- 카드 의미와 정방향 전용 MVP 조건을 의미 변화 없이 번역한다.
- 이번 변경에서 출력 분량, 카드 근거 표현, 말투 세부 규칙 등 새로운 동작은 추가하지 않는다.

## 호환성 계약

다음 값은 Java 코드, 입력 JSON 또는 JSON Schema가 사용하는 식별자이므로 번역하지 않는다.

- 스프레드 ID: `daily_one_card`, `mind_three_card`, `relationship_three_card`, `choice_five_card`
- position ID: `today`, `emotion`, `underlying_need`, `self_action`, `my_heart`, `relationship_flow`, `check_point`, `desire`, `fear`, `core_value`, `option_a`, `option_b`
- 출력 필드: `title`, `summary`, `sections`, `guidance`, `disclaimer`, `position`, `heading`, `body`
- 입력 키: `untrustedUserInput`, `question`, `readingInput`, `choiceOptions`
- 카드 ID: `major-00-fool`부터 `major-21-world`까지
- 불리언 값과 코드 표기: `false`, JSON

프롬프트는 `TarotPromptCatalog`에서 UTF-8로 읽혀 하나의 system 메시지로 조합된다. 따라서 일반 설명 문장의 언어는 런타임 파싱에 영향을 주지 않으며 위 식별자만 보존하면 된다.

## 검증

- 설정이 계속 활성 v2 파일을 가리키는지 확인한다.
- 활성 프롬프트에서 필수 식별자와 한국어 라벨이 보존되는지 테스트한다.
- `TarotPromptCatalogTests`와 `OpenAiReadingGeneratorTests`를 실행해 프롬프트 로딩, 버전 조합, system/user 메시지 구성 및 JSON Schema 계약을 검증한다.
- 관련 테스트 실패가 없으면 Java 코드 변경 없이 호환된다고 판단한다.

## 제외 범위

- 비활성 스프레드 v1 번역
- 질문 중복 제거
- 선택된 카드만 프롬프트에 포함하는 최적화
- 출력 분량 및 말투 정책 변경
- API 호출 방식이나 모델 설정 변경
