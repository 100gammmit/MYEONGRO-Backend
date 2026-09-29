# Flyway 마이그레이션 정책

MYEONGRO는 실제 production 데이터베이스를 만들기 전에 기존 V1~V19의 최종
스키마를 `V1__initialize_schema.sql` 하나로 통합했다. 이 V1은 PostgreSQL 16의
빈 데이터베이스에서 기존 전체 체인을 재생한 결과를 기준으로 작성했으며, 과거에
생성했다가 제거한 Supabase Auth/RLS 호환 객체와 데이터 변환 단계는 포함하지 않는다.

런타임은 다음 안전 설정을 유지한다.

- `spring.flyway.validate-on-migrate=true`
- `spring.flyway.clean-disabled=true`
- `spring.flyway.baseline-on-migrate=false`
- Hibernate `ddl-auto=validate`

## 변경 규칙

- production에 V1이 한 번이라도 적용된 뒤에는 V1을 수정하지 않는다.
- 이후 스키마 변경은 `V2__*.sql`, `V3__*.sql`처럼 새 versioned migration으로 추가한다.
- Flyway checksum 오류를 숨기기 위해 `repair`를 사용하지 않는다.
- 로컬 또는 공유 개발 DB에 이전 V1~V19가 기록돼 있다면 해당 비production DB를
  삭제하고 새 V1부터 다시 만든다.
- migration 실행 계정은 대상 데이터베이스에 연결할 수 있고 `public` 스키마에서
  객체를 생성할 수 있어야 한다.

## 빈 PostgreSQL 16 재생 검증

일반 개발용 PostgreSQL과 분리된 일회용 컨테이너를 사용한다. 이 저장소의
`compose.yaml`은 고정 `container_name`을 사용하므로 이 검증에 `docker compose -p`를
사용하지 않는다.

PowerShell:

```powershell
docker run -d --name myeongro-flyway-check-postgres `
  -e POSTGRES_DB=myeongro `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=flyway-check-only `
  -p 15432:5432 `
  postgres:16-alpine

.\gradlew.bat test `
  --tests com.myeongro.api.database.FlywayFreshPostgresReplayTests `
  -DfreshPostgresJdbcUrl=jdbc:postgresql://localhost:15432/myeongro `
  -DfreshPostgresUsername=postgres `
  -DfreshPostgresPassword=flyway-check-only

docker rm -f myeongro-flyway-check-postgres
```

검증은 다음 계약을 확인한다.

- 빈 `public` 스키마에 V1 하나만 적용된다.
- 최종 테이블, enum, 인덱스, 외래 키와 check constraint가 생성된다.
- 신용 차감·생성 완료·실패·오래된 생성 정리 함수가 실제 PostgreSQL에서 동작한다.
- SECURITY DEFINER 함수는 PUBLIC 실행 권한을 갖지 않는다.
- 읽기 생성과 동의 전이의 advisory lock 계약이 유지된다.
- 이전 Supabase 역할과 호환용 `auth` 스키마가 생성되지 않는다.

검증용 컨테이너는 production 데이터나 평소 개발 DB를 사용하지 않는다. 테스트가
끝나면 반드시 위 cleanup 명령으로 제거한다.
