# 사주 계산 버전 정책

`saju-ko-v1`은 아래 규칙을 하나의 재현 가능한 계산 계약으로 고정한다.
`saju-ko-v2`는 같은 산술 규칙을 유지하면서 출생시간 후보에 따라 일간이 달라져도
요청 연도의 공통 간지는 보존하고, 달라지는 세운 십성만 불확실 값으로 제외한다.

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
`cityCatalogVersion`, `targetYear`를 함께 기록한다. 실패 리딩 재시도는 저장 snapshot을
strict decode하고 계산을 다시 수행하지 않는다. 엔진·규칙·카탈로그를 변경할 때 기존
fixture 기대값을 덮어쓰지 말고 새 계산 버전을 추가한다.

골든 fixture는 `src/test/resources/saju/golden/saju-ko-v1.json`과
`src/test/resources/saju/golden/saju-ko-v2.json`에 있으며 공식 엔진 소스
`https://github.com/6tail/lunar-java/tree/v1.7.7`을 검증 출처로 기록한다.
실패 리딩 재시도는 저장된 v1과 v2 snapshot을 모두 읽을 수 있다.
