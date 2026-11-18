# 공개 만세력 M2 fixture 출처와 검증 기록

검증일: 2026-11-16. 기준 `dev/cb7f317`, 작업 브랜치 `feature/public-manseryeok`.
이 문서는 구현·검증 증거이며 공통 정책이나 공개 승인 문서가 아니다. 리뷰 승인 전에는 완료로 취급하지 않는다.

## 범위와 공개 상태

- Backend 전용 `POST /api/saju/calculations`, `GET /api/saju/calculations/birth-places`.
- `app.saju.public-calculation.enabled` 기본 false. 환경별 설정은 실제 secret 파일을 수정하지 않고 운영자가 별도로 반영한다.
- 공개 계산 `schemaVersion=1`, `calculationVersion=manseryeok-ko-v1`, `timeCorrectionVersion=meeus-noaa-v1`.
- 기존 `saju-ko-v4`, 저장 snapshot, AI 생성·프롬프트·크레딧·DB는 변경하지 않았다. 공유 검증 규칙과 후보 계산 경로만 재사용한다.
- 후속 Front/개인정보 고지/Docs 정책 정렬 전에는 켜지 않는다. 기존 AI는 legacy 계산이므로 무료 명식 기반 풀이 버튼을 연결하지 않는다.

## HTTP 계약

```json
{"birthProfile":{"calendarType":"solar","birthDate":"2000-06-15","birthTime":"14:30","birthTimePrecision":"exact","provinceCode":"11","luckDirectionBasis":"male"},"options":{"dayBoundary":"MIDNIGHT","equationOfTime":true}}
```

- 알려진 시각: 시·도 필수, 시·군·구 선택. 미상: birthTime/provinceCode/cityCode 필드를 아예 생략한다.
- options와 내부 필드는 생략 가능하나 명시적 null, 잘못된 타입, 알 수 없는 필드는 400.
- MIDNIGHT는 일주만 자정 기준이고 23시대 시주 천간은 다음 날 기준이다. HOUR_23은 23시 일주 전환. 균시차 off도 경도·시간대 보정은 유지한다.
- no-store, 출생정보 query 금지, JSON 32KiB 제한. POST IP 네트워크 HMAC/Redis 20회/분, 장애 fail closed. GET은 계산 quota를 쓰지 않는다.
- 포화 시 대기 없이 503, 인스턴스 동시 계산 2개, 후보 루프 협조적 deadline 3초.
- fingerprint는 계산 결과 비교값이며 인증·허가·클라이언트 계산 신뢰 수단이 아니다. 요청·응답·원본·fingerprint의 저장/캐시/AI 전송을 추가하지 않는다.
- 숨긴 endpoint에도 OpenAPI 설명을 제공하며, 정적 안내가 아니라 실행 gate가 공개 여부를 결정한다.

## 독립 수치·공식 절기

- NOAA/Meeus 고정값 2개, 1970/2000 JD, 초 반올림 및 EqT off 회귀.
- 별도 USNO 태양 좌표식과 1900~2099 매일 00/06/12/18시의 유효 Asia/Seoul offset 292,183개 순간을 대조, 차이 5초 이내 통과. 절대 관측 정확도 보장이 아니다.
- 공식 절입 fixture: `src/test/resources/saju/manseryeok/kasi-jie-kst.csv` 2018/2019/2025 각 12절 총 36개. 엔진과 최대 차이 30초. 각 시각 ±2분 공개 명식의 월주 및 입춘 연주 전환 통과.
- 2018은 [KASI 월력요항 발표](https://www.kasi.re.kr/publication/post/newsMaterial/5971?cPage=33)의 2017-02-27 공식 HWP 첨부(1488874825840_1.hwp), 표 나의 한국 표준시 시·분.
- 2019/2025 발표 첨부에는 절기 날짜만 있어 분 단위 기준으로 쓰지 않았다. 사용자가 공식 역서 보완을 승인했다.
- 보완 자료는 [KASI 역서](https://astro.kasi.re.kr/kor/almanac/pageView/26)의 [24기 데이터 다운로드](https://astro.kasi.re.kr/kor/almanac/solarTerms/download), 파일명 `24기입기시각(1920-2100)_20260902.txt`, 자료 기준 2026-09-01, 수집 2026-11-16. 예고 달력 페이지가 아니다.
- 보완 자료는 현대 KST(UTC+9) 고정·분 단위이며 역사적 시계 보정은 하지 않았다고 명시한다. 이번 fixture 연도에는 당시 KST와 충돌하지 않는다. 원본 전체 SHA256은 fixture에 기록했다. 확보한 36개만 검증으로 센다.
- 출처: [NOAA 구현](https://gml.noaa.gov/grad/solcalc/main.js), [유지보수 중단 및 제약](https://gml.noaa.gov/grad/solcalc/calcdetails.html), [USNO 독립 근사식](https://aa.usno.navy.mil/faq/sun_approx).

## 실제 외부 화면 비교

- 2026-11-16 에이전트가 [한국만세](https://hankookmanse.com/) 화면에 합성 표본 30개를 입력했다. 기존 예비 조사 결과를 복사하지 않았다.
- 양력/남/서울 표시 경도126.98/진태양시(경도+균시차)/야자시. 일반6, 자정·시주8, 절입6, 역사적 시간대6, 미상·근사4.
- 네 기둥·옵션은 `external-charts.csv`, 화면의 보정 분 시각은 `external-correction-times.csv`에 고정했다.
- 23건 네 기둥 일치. 나머지7건은 아래 정책·좌표 차이를 별도 assertion으로 검증했고 미해결 불일치는 없다. 미상·근사 UI에는 후보 범위 기능이 없으므로 같은 기능을 제공한다고 간주하지 않는다.
  - 1992-08-18 00:36: 시·도 경도127.0004에서 보정00:00:03/병인, 외부 표시126.98에서23:59:58/을축. 같은 경도를 직접 적용하면 일주 일치. 수초 차이로 갈리는 좌표 경계이지 외부 화면의 초 반올림까지 검증했다는 뜻은 아니다.
  - DST gap1건: 외부는1시간 이동해 명식을 보여주지만 공개 API는 INVALID_BIRTH_TIME 400.
  - DST overlap1건: 공개 API는 두 offset을 합쳐 공통 명식만 보존하고 대운 미확정.
  - 시각 미상2건: 외부는 달력 날짜 일주를 제공, 공개 API는 전일 후보의 공통 부분만 보존.
  - 근사2건: 외부 중심 시각 명식을 관측하고 공개 API의 ±60분 후보 공통 결과 및 overlap을 확인.
- 보조 [포스텔러](https://pro.forceteller.com/) 2.2: 합성 `2000-06-15 14:30`, 남/서울/양력/야자시·조자시에서 경진·임오·갑진·신미 일치. 화면은 지역시 -32분(13:58), EqT 선택은 없어 정밀 경계 기준으로 쓰지 않았다. 신살·강약·용신 등 외부 해석 문구는 제품에 가져오지 않았다.
- 지장간 배열 등 전통 규칙 차이를 외부 서비스의 다수결로 교체하지 않았다. 지장간·12운성·공망은 사용한 lunar-java 1.7.7 규칙과 별도 60일 회귀로 확인했다.

## 성능·보호 경계

- Windows, Java21.0.2, JVM 보고 CPU12개, 2 worker 동시 실행, 합성 exact/approximate/unknown 각각10회.
- 관련 검증 측정: exact 중앙16.4ms/최대29.8ms, approximate 중앙288.3ms/최대404.6ms, unknown 중앙1833.0ms/최대1919.6ms. 각 요청3초 이내. 첫 측정 cold/cache 상태에 따라 exact최대163.0ms, unknown최대1921.4ms였다.
- 별도 latch 테스트에서 두 slot 사용 중 세 번째 요청 즉시503, 실패 후 slot 반환. 실제3초 deadline callback 초과503 검증.
- 익명/회원/미완료 가입/성인 미확인 공개 접근, 보호된 사주·기록 경계 유지, rate429/Redis장애503/32KiB초과413 검증.
- 실제 로컬 Tomcat HTTP on200/off404, malformed400, no-store/쿠키 미생성, OpenAPI 포함 확인. 이 서버는 계산 경로만 실제 연결하며 rate와 인증 부수 의존성은 mock이다. 실제 운영 Redis 장애·부하·프록시 환경의 통합 시험을 대신하지 않는다.
- 신규 PostgreSQL/Redis 저장 구조·Lua 변경 없음. 외부 DB/Redis를 준비하지 않은 테스트는 skip으로 별도 기록한다.

## 시각 미상 구간 계산 최적화 (2026-11-18, `fix/saju-unknown-time-performance`)

- 원인: 시각 미상은 하루 1,440분마다 lunar-java `getLunar()`를 호출했다. 물리 코어 1개(논리 CPU 2개, Ryzen 5 5600X를 affinity로 고정, t3.small의 vCPU 2개 구조와 같음)·동시 2건에서 중앙 2,180.8ms·최대 2,643.7ms였다. t3.small 코어가 더 느린 점을 감안하면 3초 deadline 초과 위험이 있었다.
- 변경: 시각 미상(`includeTime=false`)만 같은 오프셋 구간을 이분 탐색해 값이 바뀌는 분에서만 엔진을 호출한다. 후보 수·오프셋·DST 표시는 매분 보정 단계에서 그대로 센다. 시각 확정·대략 경로는 바꾸지 않았다.
- 동등성 근거: 연·월·일주는 하루 안에서 이전 값으로 돌아가지 않는 계단 함수이고, 병합과 공개 누적기는 공통값만 남긴다. 기존 매분 탐색을 `segmentedUnknown=false` 기준 구현으로 남겨 `Resolution` 전체와 누적기 출력(변동 필드 순서 포함)을 대조한다.
  - 기본 `test`: 경계일 8개 × LEGACY·정밀(HOUR_23, 균시차 끔), 극단 경도 2곳.
  - `slowTest`: 1900~2099 모든 Asia/Seoul 오프셋 전환일, 1985·2020 절기일, 무작위 120일(seed 20261118) × 설정 5종, 전환일 × 극단 경도. 2026-11-18 실행: 211일·1,211건 전부 일치, 1,006초(6병렬).
- 같은 조건 재측정: 시각 미상 중앙 43.0ms·최대 256.3ms(첫 호출 포함), 확정 중앙 41.0ms, 대략 중앙 493.9ms. 엔진 호출은 하루 64회 이하로 테스트한다.
- 시작 예열 `SajuCalculationWarmup`: 합성 입력으로 LEGACY·공개 경로의 확정·대략·미상을 1회씩 실행한다. 같은 코어 1개 조건에서 예열 2,065ms, 예열 후 첫 요청은 확정 53.8ms·대략 636.9ms·미상 53.4ms였다(예열 전 첫 호출 최대 확정 1,939.7ms·대략 2,706.2ms). `app.saju.calculation-warmup.enabled=false`로 끌 수 있다.
- 전체 `test`: 98 suites/459 tests, 실패0/오류0, skip3(기존 PostgreSQL·Redis 조건부). 기존 golden 기대값은 수정하지 않았다.
- 운영 t3.small 실측은 아직 하지 않았다.

## 실행 결과

- 관련 계산·입력·관계·설정 바인딩·보안·HTTP/legacy golden 테스트: BUILD SUCCESSFUL.
- 설치된 Gradle8.14.5 `test` 전체 검증1회: BUILD SUCCESSFUL(53초), 96 suites/438 tests, 실패0/오류0, skip3(따라서 실행 통과435). 기존 AI golden 기대값을 수정하지 않았다.
- skip: Fresh PostgreSQL replay, PostgreSQL monthly-calendar persistence, 실제 Redis가 필요한 전체 application context. 외부 서비스 설정을 제공하지 않아 조건부 skip이며 통과로 세지 않는다. 이번 변경에는 migration/DB 함수/영속화 경로 수정이 없다.
- 전체 실행의 합성 성능 재측정도3초 게이트 통과: exact 최대33.3ms, approximate415.8ms, unknown1982.6ms.
- 전체 검증 뒤 `test --tests '*Manseryeok*HttpSmokeTests'` 재실행 BUILD SUCCESSFUL(8초), 실제 로컬 HTTP/OpenAPI on/off2건 통과.
- `git diff --check` 통과. Gradle wrapper 환경 잠금을 피해 동일 버전의 설치된 Gradle을 사용했다. 다른 worktree/프로세스/lock을 변경하지 않았다.
- 초기 테스트에서 옵션 생략과 null 구분, 설정 record의 생성자 바인딩, 테스트용 HTTP 컨텍스트 격리 문제를 발견·수정했다. 실패했던 실행을 통과로 계산하지 않는다.
- 공개 활성화/유료 AI 연결/운영 동시 부하는 후속 범위다. push/PR/merge/deploy는 수행하지 않는다.

## 첫 리뷰 반영

- 3f227f5 기준 changes-requested: P1 경로 판정 불일치, P2 공망 기준 기둥, P2 공개 varyingFields 경로. 동일 브랜치 후속 커밋에서 보정한다.
- 공개 gate·rate·세션 예외·인가 모두 동일한 Spring Security PathPatternRequestMatcher를 공유한다. 인코딩된 경로도 실제 로컬 HTTP에서 off404/LIMITED429/UNAVAILABLE503, 정상 on200 및 quota 호출을 검증한다.
- 공망은 후보 공통값뿐 아니라 최종 일주·연주 확정을 각각 요구한다. unknown/approximate의 같은 순 공망과 입춘 연주 미확정 회귀를 추가했다.
- varyingFields는 공개 projection에서 chart.pillars.*, annualFlows.*.stemTenGod, monthlyCalendar.months.*.periods.*.stemTenGod로 변환한다. `*`는 배열/맵의 각 항목을 뜻하는 필드 경로다. legacy snapshot 경로는 변경하지 않는다.
- 일간 미확정으로 가린 파생 12운성도 공개 미확정 항목에 남긴다. 전체 검증은 앞의1회 결과로 구분한다.
- 보정 후 공개 만세력 전체 관련 테스트, 기존 Golden/M3/corrector/resolver, IpRateLimit, AuthenticatedReadingSecurity를 실행: 15 suites/50 tests, 실패·오류·skip0, BUILD SUCCESSFUL(42초). 실제 HTTP의 인코딩 경로 회귀와 공망·공개 경로 assertion을 포함한다. `git diff --check`도 통과했다.
- 재리뷰에서 지장간 십성 경로의 배열 단계 누락1건이 남아 `hiddenStems.year.*.tenGod`/`hiddenStems.month.*.tenGod`로 통일했다. 연·월 지장간은 남고 일간만 미확정인 unknown/approximate 합성 표본에서 배열·미확정 십성·올바른 경로 및 잘못된 경로 부재를 함께 검증한다.
- 잔여1건 보정 후 Calculation/ChartBuilder/ExternalReference/실제HTTP on·off 관련5 suites/14 tests 모두 통과, BUILD SUCCESSFUL(18초), diff check 통과. 두 번째 전체 suite는 실행하지 않았다.
