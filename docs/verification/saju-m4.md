# M4 한 해 리포트 검증 기록

검증일: 2026-11-05. 요청 스레드: 019f9cf3-d800-73b2-b3b7-086f7416ec9f.

## 범위와 결정

- M4-A 실제 합성 Preview 검증은 `saju-m4a-preview.md`에 기록했다. M4-A 재리뷰 승인 커밋은 c7a65677eacdb7a1087192cc42b71354e800c9eb이다.
- 사용자 승인으로 상세형·일간 미확정 간략형 모두 4크레딧을 적용한다. M4-B에서는 실제 AI 호출을 추가하지 않았다.
- 기존 종합 사주 schema v6·지문·프롬프트를 유지하고, 한 해 리포트만 schema v7로 저장한다. 원본 출생정보·질문·관심 분야는 기록에 저장하지 않는다.
- 기존 SQL 생성 예약·완료 함수를 유지한다. 멱등성·원자적 차감 경계이므로 JPA CRUD로 변경하지 않았다. V4는 v5/v6을 보존하며 v7 입력 제약만 추가한다.

## 관련 검증

- Backend: ReadingEndpointContractTests, ReadingCreationWorkflowTests, SajuReadingInputAssemblerTests, SajuAnnualTests, ReadingCreditPropertiesTests, ReadingGeneratorRouterTests, SajuAnnualProductionTests 통과.
- 실제 PostgreSQL 16의 작업 전용 격리 DB에서 FlywayFreshPostgresReplayTests 통과: 빈 DB부터 V1~V4 재생, 기존 v5 유지, v6/v7 입력 분리, 연간 완료 시 4 차감·실패 시 미차감.
- Front: 신규 연간 parser·화면·기존 입력 여정 관련 테스트 통과. 승인된 실제 합성 Preview의 상세형·간략형을 v7 저장 기록 모양으로 복원했다.

## 최종 종합 검증 1회

- Backend `test bootJar` 실행: 394개 중 2개 실패. 패키징은 성공했다. 실패는 연간 의존성을 빠뜨린 테스트 전용 bean 구성과 기본 Redis 6379 연결이었다.
- 테스트 bean 구성에 SajuAnnualConfiguration을 포함하고, 작업 전용 Redis 포트를 환경 변수로 지정해 **실패한 ReadingGeneratorBeanSelectionTests·MyeongroBackendApplicationTests 2개만 재실행하여 통과**했다. 전체 테스트는 반복하지 않았다.
- Front: 전체 79 files / 517 tests, typecheck, lint, production build 통과. 기존 jsdom의 navigation/scrollTo 미구현 stderr는 테스트 실패가 아니다.
- Docs: `python -m unittest discover -s tests` 16개 통과.
- 각 저장소 `git diff --check` 통과.

## 브라우저·시각 검증

- `node scripts/check-saju-annual.mjs`: 로컬 합성 API와 승인된 합성 Preview 결과로 상세형·간략형, 12개월 버튼, 절입 전후 한국 시각, 공유 구간, Enter/Space, 375px·1280px, month URL 새로고침, 기록 복원, 무질문 생성 통과. 유료 AI·실제 로그인·사용자 DB를 사용하지 않았다.
- `node scripts/check-saju-explorer.mjs`: 기존 종합 사주 탐색, 오행·합/파 중첩·관계 없음·시간 미상, 키보드·기록 복원 회귀 통과.
- 상세형 375px·1280px과 간략형 375px 스크린샷을 직접 확인했다. 간략형에서 존재하지 않는 구간 장문을 암시하지 않도록 안내를 조정하고 제한사항 제목/본문의 줄을 구분했다. 마지막 수정 뒤 해당 컴포넌트 2개 테스트, production build, 연간 브라우저 시나리오를 다시 통과했다. 전체 테스트는 반복하지 않았다.
- 스크린샷은 Front `build/saju-annual-check`에만 보관한다. AI 호출 없이 만든 로컬 검증 자료이며 배포 콘텐츠가 아니다.

## 한계와 배포 경계

- 새 공개 생성 경로를 실제 사용자 계정·실제 OpenAI로 다시 호출하지 않았다. M4-A 실제 생성 검증, M4-B mock 및 실제 DB 테스트, 합성 브라우저 검증을 결합했다.
- 스크린리더 프로그램을 직접 실행한 검증은 아니며 네이티브 버튼·details·aria-pressed/controls·live heading과 키보드 접근을 확인했다.
- 소수 합성 Preview 품질 평가는 운영 품질 보증이 아니다. 운영 동시 부하·광범위 사용자 품질은 후속 검증 대상이다.
- Backend V4와 Front v7 지원은 함께 반영한다. M3 부모 커밋을 보존하며 사용자의 push·PR·merge 전에는 Review Desk 최종 승인이 필요하다.
