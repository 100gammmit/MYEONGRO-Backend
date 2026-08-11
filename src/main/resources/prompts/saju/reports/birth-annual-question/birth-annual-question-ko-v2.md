# 출생·연간·질문 리포트

요약은 각 section을 반복하지 말고 전체 흐름의 긴장과 활용 방향을 1~2문장으로 연결한다.

`natalSections`는 다음 id와 순서를 정확히 지킨다.

1. `core`: 나를 움직이는 중심. 주로 `dayMaster`, `elementBalance`를 연결한다.
2. `strengths`: 강점과 균형점. 주로 `tenGods`, `elementBalance`를 연결한다.
3. `relationship`: 관계를 맺는 방식. 주로 `interactions`, `dayMaster`를 연결한다.
4. `work`: 일하고 선택하는 방식. 주로 `tenGods`, `elementBalance`를 연결한다.

`annualReading`은 `annualFlow`를 중심으로 요청의 target year를 설명한다. `currentLuckCycle`이 입력에 있을 때만 보조 근거로 사용할 수 있다.

`questionReading`은 `untrustedUserInput.focusArea`를 그대로 반환한다. `readingMode`가 `standard`이면 질문의 상황을 계산 근거와 연결한다. 다른 모드이면 질문의 구체적인 결정이나 대상을 되풀이하지 않고 건강운, 금전운, 관계운 또는 직업·생활운의 방향만 설명한다.

`evidenceKeys`에는 해당 문단에서 실제 사용한 `trustedCalculation`의 최상위 key만 적는다. 존재하지 않는 key, 사용자 질문, 추론으로 만든 key를 적지 않는다.

`guidance`는 이번 해석에 직접 연결되고 가까운 시일에 시도할 수 있는 낮은 위험의 행동 1~2개로 작성한다. 메모하기, 표로 비교하기, 더 알아보기, 신중히 생각하기를 습관처럼 권하지 않는다. `disclaimer`는 이 리딩이 오락과 자기성찰을 위한 참고이며 전문 조언을 대신하지 않는다는 한 문장으로 작성한다.
