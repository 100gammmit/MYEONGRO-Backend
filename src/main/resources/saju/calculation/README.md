# 사주 계산 버전 정책

`saju-ko-v1`은 아래 규칙을 하나의 재현 가능한 계산 계약으로 고정한다.
`saju-ko-v2`는 같은 산술 규칙을 유지하면서 출생시간 후보에 따라 일간이 달라져도
요청 연도의 공통 간지는 보존하고, 달라지는 세운 십성만 불확실 값으로 제외한다.
`saju-ko-v3`는 출생지를 시·도 단위로 간소화하고 시·도 내부 행정구역 좌표의 중앙값을
진태양시 보정에 사용한다. 출생시간 미상은 대한민국 표준 자오선을 기준으로 계산한다.
`saju-ko-v4`는 v3와 같은 산술 규칙을 유지하면서 두 가지를 바꾼다. 첫째, 엔진이 돌려주는
한자 간지·오행과 중국어 간체 십성을 snapshot에 저장하기 전에 한국어 명리 용어(`SajuKoreanTerms`)로
바꾼다. 계산 규칙 판정은 여전히 엔진의 한자 값으로 하고, 합충 종류는 코드(`branch_clash` 등)로 둔다.
둘째, 오행 분포는 후보끼리 같은 값만 남기지 않고 모든 후보에 공통으로 확정된 기둥만으로 다섯 오행을
모두 센다.

- 계산 엔진: `lunar-java` `1.7.7`
- 출생지 카탈로그: 계산 시점의 `cityCatalogVersion`을 snapshot에 저장
- 시간대: 생년월일 당시의 `Asia/Seoul` UTC offset
- 절입 엔진 시각: 출생 민간시각을 instant로 확정한 뒤 UTC+8로 변환해 엔진의 절입 시각과 비교
- 진태양시: `(출생지 경도 - 당시 UTC offset 기준 자오선) × 4분 + 균시차`를 민간시각에 적용하고 일주·시주 계산에만 사용
- 연주·월주·대운 시작: UTC+8 엔진 시각과 엔진의 절입 시각 기준 exact API 사용
- 일 경계: `EightChar` sect 2, 늦은 자시를 당일 일주로 처리
- 대운 시작: 분 단위 계산인 `Yun` sect 2 사용
- 세운: 저장된 `targetYear`의 입춘 이후 연주를 사용

정확 시각은 유효한 instant를 계산한다. 근사 시각은 입력 시각 전후 60분의 분 단위 후보를
계산하고 모든 후보에 공통인 사실만 snapshot에 남긴다. 미상 시각은 출생일 전체 1,440개
분 범위를 계산하며 시주와 대운은 항상 제외한다. 한국의 과거 DST 전환으로 존재하지 않는
민간시각은 exact 입력에서 거부하고 근사·미상 범위에서는 건너뛴다. 두 번 존재하는 민간시각은
두 offset의 instant를 모두 후보에 포함하고 `DST_OVERLAP_AMBIGUOUS` limitation을 남긴다.

저장 snapshot에는 `calculationVersion`, `engine`, `engineVersion`,
`cityCatalogVersion`, `targetYear`를 함께 기록한다. 기존 기록 조회는 저장된 계산정보를
사용하고 계산을 다시 수행하지 않는다. 실패 리딩은 새 입력·새 requestId로 요청한다. 엔진·규칙·카탈로그를 변경할 때 기존
fixture 기대값을 덮어쓰지 말고 새 계산 버전을 추가한다.

골든 fixture는 `src/test/resources/saju/golden/saju-ko-v1.json`과
`src/test/resources/saju/golden/saju-ko-v2.json`, `src/test/resources/saju/golden/saju-ko-v3.json`,
`src/test/resources/saju/golden/saju-ko-v4.json`에 있으며 공식 엔진 소스
`https://github.com/6tail/lunar-java/tree/v1.7.7`을 검증 출처로 기록한다. v4 fixture는 v3와 같은
케이스의 기대값을 한국어 용어로 옮긴 것이다.
기존 v1~v4 계산 fixture는 보존하며, 해석 입력 변환은 한자와 한국어 용어를 모두 받는다.

## 월운 달력 `saju-monthly-ko-v1`

사주 schema v6은 원국 `saju-ko-v4`를 변경하지 않고 최소 snapshot에 `monthlyCalendar`를 추가한다.
원본 출생정보는 여전히 요청 중에만 사용한다. 월운은 출생시각 후보를 병합한 뒤 한 번 계산하며,
선택한 연도의 달력 1~12월 각각을 절입 전·후 두 구간으로 나눈다. 절입 시각은 lunar-java의
UTC+8 민간시각에서 instant를 거쳐 Asia/Seoul로 변환한다. 대상 연도 달력에 출생지의 진태양시 보정을 적용하지 않는다.

각 구간은 `startInclusive`(시작 포함), `endExclusive`(종료 미포함), `yearGanZhi`, `monthGanZhi`를 갖는다.
`stemTenGod`는 출생 후보들에서 일간이 공통으로 확인될 때만 저장한다. 입춘 전 구간의 연간지는
전년도 간지일 수 있으며 달력 연도 `targetYear`와 혼동하지 않는다. timestamp는 초 단위 한국 시각이다.
공개 API는 한국 날짜 기준 올해·내년을 선택한다. 명시 연도는 요청 지문에 포함하고, 완료된 요청의
멱등 조회가 새로운 생성의 연도 유효성 검사보다 먼저 수행되어 연말 재전송도 기존 결과로 수렴한다.

월운 계산 재현 fixture는 `src/test/resources/saju/golden/saju-monthly-ko-v1.json`이다.
기존 원국 골든과 2020년 입춘 경계를 유지하며 2027년 전체 절입을 고정한다. 이 fixture는 지정 엔진의
계산 계약 재현성을 검증하며 천문학적 오차나 미래 예측의 정확성을 입증하는 자료가 아니다.
M3에서는 월운 달력을 DB snapshot에만 담고 현재 OpenAI 해석 입력에는 전달하지 않는다.
기존 DB v5 기록은 migration으로 재계산하지 않으며 월별 해석 생성/표시는 M4의 별도 범위다.
