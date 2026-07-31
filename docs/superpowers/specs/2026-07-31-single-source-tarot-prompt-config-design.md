# 타로 프롬프트 활성 버전 단일 설정 설계

## 목표

새로운 타로 프롬프트 리소스를 추가할 때 활성 버전을 선택하는 수정 지점을
`src/main/resources/application.yaml` 하나로 제한한다. Java 운영 코드, Java 테스트,
테스트용 YAML은 `v5`, `v4` 같은 구체 버전을 알지 않아야 한다.

`generation_records.prompt_version` 기록은 유지한다. 런타임은 운영 YAML에서 선택된
리소스의 파일명을 읽어 기존 방식대로 prompt version을 자동 생성한다.

## 현재 문제

운영 YAML 외에도 다음 위치가 활성 프롬프트의 구체 버전 파일명을 참조한다.

- `src/test/resources/application.yaml`
- `OpenAiReadingGeneratorTests`
- `ReadingGeneratorBeanSelectionTests`

이 참조들은 운영 활성 버전 계약이 아니라 테스트를 실행하기 위한 입력인데도 실제
운영 리소스를 fixture로 사용한다. 이 때문에 프롬프트 버전을 올릴 때 테스트 코드와
테스트 설정도 함께 바꾸게 된다.

## 설계

### 운영 설정

운영 프롬프트의 활성 리소스 경로는 기존처럼
`src/main/resources/application.yaml`에서 관리한다. common, cards, 네 종류 spread의
총 여섯 경로가 유일한 활성 버전 선택 지점이다.

`TarotPromptCatalog`는 구체 버전을 하드코딩하지 않는다. 주입된 각 리소스의 파일명에서
`.md`를 제거해 version을 만들고, common/cards/spread version을 조합하는 기존 동작을
유지한다.

### 테스트 fixture

`src/test/resources/prompts/tarot/fixtures` 아래에 버전 없는 고정 fixture를 둔다.

- `common.md`
- `cards.md`
- `spreads/daily-one-card.md`
- `spreads/mind-three-card.md`
- `spreads/relationship-three-card.md`
- `spreads/choice-five-card.md`

fixture는 런타임 파서와 합성 계약을 검증하는 데 필요한 최소 구조만 포함한다.
`cards.md`는 canonical major arcana ID 22개를 한 번씩 포함하고, spread fixture는 각
spread의 position 및 출력 계약을 표현한다. 실제 사용자용 문구나 운영 버전은 복제하지
않는다.

### 테스트 설정과 Java 테스트

`src/test/resources/application.yaml`과 직접 `TarotPromptCatalog`를 만드는 Java 테스트는
버전 없는 fixture 경로만 사용한다. 이 경로는 테스트 계약의 일부이므로 새로운 운영
프롬프트가 추가되어도 변경하지 않는다.

`ApplicationYamlContractTests`는 현재의 버전 중립 검증을 유지한다. 운영 YAML의 여섯
설정 키가 존재하고, 값이 `classpath:` 경로이며, 실제 리소스가 존재하고 읽을 수 있는지만
확인한다. 구체 파일명이나 버전은 assertion하지 않는다.

## 데이터 흐름

1. 운영자는 새 버전의 Markdown 리소스를 추가한다.
2. 운영 `application.yaml`의 해당 경로만 새 파일로 변경한다.
3. 애플리케이션 시작 시 `TarotPromptCatalog`가 선택된 리소스를 읽는다.
4. 리딩 생성 시 선택 리소스의 파일명으로 prompt version을 자동 조합한다.
5. `ReadingGenerationMetadataResolver`가 해당 값을 기존대로 generation record에 전달한다.

테스트는 이 흐름과 별개로 고정 fixture를 사용하므로 운영 버전 변경의 영향을 받지 않는다.

## 오류 처리와 보호 범위

- 운영 YAML에서 필수 키가 빠지면 Spring 주입 또는 운영 YAML 계약 테스트가 실패한다.
- 경로가 존재하지 않거나 읽을 수 없으면 계약 테스트 또는 카탈로그 초기화가 실패한다.
- 빈 프롬프트, 잘못된 확장자, 카드 ID 누락·중복·미지원 값은 기존
  `TarotPromptCatalog` 검증이 계속 차단한다.
- 구체 버전 문자열의 정확성은 테스트가 고정하지 않는다. 선택된 파일명이 곧 기록되는
  버전이라는 기존 규칙을 따른다.

## 검증

- 저장소 검색에서 `common-ko-v숫자`, `major-arcana-ko-v숫자`, spread의 `-ko-v숫자`
  참조가 운영 `application.yaml` 이외의 Java/YAML 코드에 남지 않았는지 확인한다.
- `OpenAiReadingGeneratorTests`, `ReadingGeneratorBeanSelectionTests`,
  `ApplicationYamlContractTests`, `ReadingGenerationMetadataResolverTests`를 실행한다.
- 마일스톤 종료 직전에 전체 Gradle 테스트를 한 번 실행한다.

## 비범위

- 프롬프트 Markdown 본문의 수정
- prompt version 저장 형식 변경
- 가장 높은 버전 파일을 자동 탐색하는 기능
- 기존 prompt version 또는 generation record 마이그레이션
