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
Markdown은 정상 결과의 리딩 본문, 요약, 고지문을 보여 주고 거절 결과의 안내는 별도로 유지한다. 모델, 프롬프트,
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

운영과 Preview 모두 `ReadingRequestJson`으로 USER 메시지의 모든 Map 키를
문자열 오름차순으로 직렬화한다. 중첩 Map과 배열 안의 Map에도 적용하지만,
카드·계산 결과의 배열 순서와 값은 바꾸지 않는다. SYSTEM → USER 메시지 순서와
프롬프트 합성 순서는 기존대로 유지한다. 이는 요청의 변동 요인을 제거하는 계약이며,
응답 Structured Output 스키마의 속성 순서나 리딩 품질 개선을 보장하는 것은 아니다.

## M4-A 한 해 리포트 비공개 평가

한 해 리포트의 USER 입력 `calendarMonths`에는 `periodIds`와 같은 순서의 `periodDays`가 포함된다. 각 값은 해당 구간이 그 달 안에서 차지하는 실제 경과 시간을 일수로 환산하고 소수 첫째 자리에서 표시한 값이다(소수 둘째 자리 반올림). 달을 걸치는 공유 구간의 전체 길이가 아니라 달별로 잘린 길이를 사용한다. 운영과 Preview가 같은 `SajuAnnualCalendar` 계산을 사용하므로 별도 주입기는 필요 없다. 이 값은 AI 입력 정보이며 공개 응답·저장 snapshot의 필드를 추가하지 않는다. 날짜 비중을 해석에서 어떻게 사용하는지는 별도 프롬프트 채택 사항이다.

한국어 기획서는 `docs/plans/saju-m4-annual-report.md`다. M4-A의 격리 Preview로 검증한 생성기를 M4-B에서 기존 SAJU handler에 위임 연결한다. 별도의 중복 SAJU handler를 등록하지 않으며 기존 종합 사주의 schema v6과 활성 프롬프트는 그대로다. 한 해 리포트는 schema v7, 사용자 승인 비용은 상세형·간략형 모두 4크레딧이다.

`SajuAnnualPreviewFixtureTests`는 기본적으로 @TempDir에만 합성 fixture를 만들고 검증한다. 명시적으로 export 속성을 켰을 때만 `build/saju-annual-preview/cases`에 8개를 만든다. 원본 출생정보를 제거한 최소 snapshot만 내보내며 개발자의 source Preview fixture를 덮어쓰지 않는다. Preview는 같은 이름의 classpath fixture가 없을 때 이 build 경로를 읽는다. 각 fixture의 `input`은 `reportType`, `targetYear`, `focusArea`, `calculationSnapshot`으로 제한한다. 질문은 비어 있어도 되며 직접 식별정보와 길이 검사는 provider 호출 전에 적용한다.

```powershell
.\gradlew.bat test --tests '*SajuAnnualPreviewFixtureTests' -DsajuAnnualPreviewExport=true
.\gradlew.bat aiPreview -Pkind=saju -Pcase=annual-self
.\gradlew.bat aiPreview -Pkind=saju -Pcase=annual-self -Pexecute=true
```

연간 결과의 `months`는 달력 1~12월의 요약이며 `periods`는 상세형(full)에서만 연속한 동일 월운을 합친 구간별 해석을 담는다. 일간 미확정 간략형(brief)은 periods=[]이며 연간 개요·월별 한 줄 요약·계산 구간만 제공한다. detailLevel은 서버가 계산 근거로 정하며 임의로 full을 선택하지 못한다. 구간 ID는 계산 순서대로 부여하며 월별 참조와 적용 기간은 계측 JSON의 `metrics.calendar`에 있다. AI에는 날짜·출생 원문을 보내지 않는다. `questionReading`은 질문이 없으면 null이다. 서버는 월/구간 누락·순서·중복·두 구간 근거 누락·다른 구간 근거·없는 근거를 거부한다.

계측은 실제 응답 모델, 입력/출력/전체 토큰, 종료 이유와 호출 시간을 보관한다. provider 응답 후 계약 검증 실패도 원문 응답 없이 토큰과 고정 진단 코드만 남긴다. 근거 키 검증 실패는 `contractCode`에 더해 `evidenceIssue`(누락·빈 목록·중복·미지원·필수 근거 누락)와 `contractPath`(서버가 정한 필드 위치)를 남긴다. 잘못 온 키 문자열이나 질문·본문은 진단에 복사하지 않는다. 캐시 읽기/쓰기와 계정별 요율을 기록하지 않으므로 기본 단가 추정은 청구액이 아니다. 실제 사용자 데이터는 사용하지 않는다.

현재 엔진에서 시간 미상은 하루 전체를 검사해 일간도 달라질 수 있다. `annual-stable-day`는 이와 구분해 정오의 근사 시각(시각 불확실·일간 확정)으로 만들고 `annual-unknown`은 실제 시간 미상·일간 미확정으로 검증한다.

M4-A 평가 후 품질·시간·비용을 보고하고 연간 크레딧을 사용자에게 확인받기 전에는 M4-B로 넘어가지 않는다. 전체 검증과 최종 Notion 완료 보고는 M4 전체 종료 때 한 번 수행한다.
