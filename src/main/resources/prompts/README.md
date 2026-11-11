# 리딩 프롬프트 보존 정책

운영 리소스에는 현재 활성 파일과 각 용도의 직전 활성 파일만 보존한다. 직전은 파일명의 숫자가 아니라 활성 설정의 변경 이력으로 판단한다. 일반 사주와 연간 사주가 공유 디렉터리에서 서로 다른 파일을 사용하므로 두 용도의 보존 집합을 합친다.

| 용도 | 현재 | 직전 보존 |
| --- | --- | --- |
| 공통 안전 | reading-safety-ko-v3 | v2 |
| 타로 common | common-ko-v165 | v107 |
| 타로 카드 사전 | major-arcana-ko-v4 | v3 |
| 고민 | mind-three-card-ko-v73 | v70 |
| 관계 | relationship-three-card-ko-v24 | v21 |
| 선택 | choice-five-card-ko-v20 | v18 |
| 일반 사주 common | common-ko-v10 | v9 |
| 일반 사주 해석 가이드 | interpretation-guide-ko-v5 | v4 |
| 일반 사주 리포트 | birth-annual-question-ko-v6 | v5 |
| 연간 사주 common | common-ko-v11 | 별도 이전 활성 파일 없음 |
| 연간 사주 해석 가이드 | interpretation-guide-ko-v7 | 별도 이전 활성 파일 없음 |
| 연간 사주 리포트 | annual-monthly-ko-v27 | v23 |

현재 경로의 기준은 일반 리딩의 `application.yaml`과 연간 리딩의 `SajuAnnualPromptCatalog`다. 이 표는 설정을 대체하지 않으며 활성 파일 변경 시 함께 갱신한다.

## 변경과 복구

- 이미 사용한 버전의 본문을 덮어쓰지 않고 새 버전을 추가한다.
- 새 조합 검증 후 현재 설정과 직전 보존 파일을 갱신한다. 더 오래된 버전은 Git 이력으로 복구할 수 있다. 미커밋 후보는 이력이 없으므로 제거하기 전에 로컬 보관한다.
- 개발 폴더의 `prompts/legacy/activated`와 `prompts/legacy/inactivated`는 저장소 밖의 로컬 보관소이며 런타임 참조 대상이 아니다. 새 checkout에는 함께 내려오지 않는다.
- 테스트만 사용하는 구버전은 `src/test/resources/prompts`에 고정 fixture로 둔다. 운영 리소스의 보존 개수를 늘리기 위해 남기지 않는다.
- 직전 리소스의 보존이 현재 Java·JSON 계약과의 롤백 호환성을 보장하지는 않는다. 특히 카드 parser나 guidance 계약이 바뀐 경우 코드·설정의 해당 Git 커밋을 함께 검토한다.
- 프롬프트 정리 때문에 파일명 기반 `prompt_version`이나 현재 생성 결과를 바꾸지 않는다.
- 활성 버전 변경과 이 정리 변경을 서로 다른 브랜치에서 진행했다면 병합 후 보존 집합을 다시 확인한다. 예를 들어 별도 검증된 일반 사주 v16을 활성화하면 직전은 v6이 되고, v5는 운영 보존 대상에서 제외한다. 이 정리 커밋 자체는 v16 활성화를 포함하지 않는다.
- 구버전 제거 전에 운영 설정·Java의 고정 리소스 참조·테스트 참조를 확인한다. 아직 테스트에서 필요한 리소스는 같은 classpath의 test 리소스로 옮기고, 없는 리소스 참조를 남기지 않는다.
