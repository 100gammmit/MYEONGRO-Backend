# AI Reading Preview

모델과 프롬프트 경로는 운영 코드와 동일한 `src/main/resources/application.yaml`을
사용한다. `aiPreview` Gradle 작업은 ignored 로컬 secret만 추가로 import하며,
별도의 preview 설정 파일을 유지하지 않는다.

기본 모델은 `gpt-6-luna`다. 로컬 secret의 `openai.model`이나 외부 설정이 있으면
기본값보다 우선하므로, 모델 전환 시 해당 설정도 갱신하고 dry-run의 `model`을 확인한다.

프론트엔드, 웹 서버, PostgreSQL, Redis, 로그인 없이 현재 백엔드의 타로·사주
프롬프트 결과를 로컬에서 확인한다.

이 도구는 운영 요청 로그가 아닌 개발·평가 전용 CLI다. fixture와 실행 결과에는
실제 이용자의 질문, 출생정보, 이메일, OAuth 식별자 등 개인정보를 사용하지 않고
합성된 평가 데이터만 사용한다.

기본 명령은 fixture를 로드해 사용할 프롬프트 정보와 예상 호출만 출력한다.
Controller와 운영 input normalizer를 거치지 않으며, fixture의 전체 입력 계약을
자동 검증하지 않는다. 실제 OpenAI 호출은 `-Pexecute=true`를 명시해야 한다.

```powershell
# 타로 호출 계획 확인
.\gradlew.bat aiPreview -Pkind=tarot -Pcase=sample-mind-three-card

# 타로 실제 호출
.\gradlew.bat aiPreview -Pkind=tarot -Pcase=sample-mind-three-card -Pexecute=true

# 사주 실제 호출
.\gradlew.bat aiPreview -Pkind=saju -Pcase=sample -Pexecute=true

# 필요할 때만 모델 덮어쓰기
.\gradlew.bat aiPreview -Pkind=tarot -Pcase=sample-mind-three-card -Pmodel=gpt-6-luna -Pexecute=true
```

실제 호출 결과는 다음 위치에 저장된다.

```text
build/ai-preview/<yyyyMMdd-HHmmss-kind>/
├─ <kind>-<case>.json
└─ <kind>-<case>.md
```

라벨은 실행할 때마다 시스템 로컬 시간과 리딩 종류로 자동 생성된다. 예를 들어
`20260810-152345-tarot` 또는 `20260810-152345-saju` 형식이며, 별도의 `-Plabel`
입력은 사용하지 않는다.

JSON은 전체 payload와 실행 정보를 보관하고 IDE diff에 사용한다. 같은 폴더의
Markdown은 리딩 본문, 실천 가이드, 고지문을 먼저 보여 주고 모델, 프롬프트,
로컬 생성 시간과 소요 시간은 하단 실행 정보로 정리한다. 같은 프롬프트 파일명을
유지한 채 내용을 수정해도 SHA-256으로 변경 전후를 구분할 수 있다.

새 fixture는 아래 경로에 추가한다.

```text
src/aiPreview/resources/ai-preview/cases/tarot/
src/aiPreview/resources/ai-preview/cases/saju/
```

Git으로 관리하는 sample fixture는 종류별로 다음 하나씩이다.

```text
tarot/sample-mind-three-card.json
saju/sample.json
```

나머지 평가 fixture는 Git에서 제외하며 로컬 파일은 그대로 사용할 수 있다.
새 케이스는 sample을 다른 이름으로 복사하고 내부 `id`도 파일명과 맞춰 수정한다.
평가 결과는 기존처럼 Git에서 제외된 `build/ai-preview/`에 저장한다.

Tarot sample은 `mind_three_card`의 카드 수와 canonical position 순서를 사용한다.
다른 spread를 평가할 때는 해당 카드 수와 position 순서를 맞추고,
choice spread에는 `choiceOptions.a`, `choiceOptions.b`를 포함한다.

sample을 복사하거나 수정한 뒤에는 실제 호출 전에 운영 코드와의 입력 계약을
수동으로 리뷰한다. 필요하면 이 작업을 Codex의 다른 스레드에 요청한다. dry-run
성공은 JSON 역직렬화와 프롬프트 조합이 가능하다는 뜻이며, 운영 입력 계약 전체가
유효하다는 보장은 아니다.

fixture에는 실제 서비스 생성기가 받는 정규화된 입력을 저장한다. 특히 사주는
프롬프트 변경만 비교할 수 있도록 생성기에 전달하는 계산 근거 `calculation`을 고정한다.
