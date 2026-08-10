# AI Reading Preview

모델과 프롬프트 경로는 운영 코드와 동일한 `src/main/resources/application.yaml`을
사용한다. `aiPreview` Gradle 작업은 ignored 로컬 secret만 추가로 import하며,
별도의 preview 설정 파일을 유지하지 않는다.

프론트엔드, 웹 서버, PostgreSQL, Redis, 로그인 없이 현재 백엔드의 타로·사주
프롬프트 결과를 로컬에서 확인한다.

기본 명령은 fixture와 프롬프트를 검증하고 예상 호출만 출력한다. 실제 OpenAI
호출은 `-Pexecute=true`를 명시해야 한다.

```powershell
# 타로 호출 계획 확인
.\gradlew.bat aiPreview -Pkind=tarot -Pcase=mind-basic

# 타로 실제 호출
.\gradlew.bat aiPreview -Pkind=tarot -Pcase=mind-basic -Pexecute=true

# 사주 실제 호출
.\gradlew.bat aiPreview -Pkind=saju -Pcase=career-basic -Pexecute=true

# 필요할 때만 모델 덮어쓰기
.\gradlew.bat aiPreview -Pkind=tarot -Pcase=mind-basic -Pmodel=gpt-5.6-luna -Pexecute=true
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

fixture에는 실제 서비스 생성기가 받는 정규화된 입력을 저장한다. 특히 사주는
프롬프트 변경만 비교할 수 있도록 계산 결과인 `calculationSnapshot`을 고정한다.
