# 백엔드 배포 준비 사항

production 배포 흐름은 다음과 같다.

`GitHub Actions → ECR → SSM Run Command → EC2 Docker Compose`

GitHub Actions는 production 애플리케이션 비밀값을 읽지 않는다. 실제 비밀값은 EC2가 SSM
SecureString에서 직접 받아 배포용 env 파일로 만든다.

## 작업 시점 구분

### 지금 준비할 것

- Frontend와 API의 운영 도메인 이름을 정한다.
- Google/Kakao 운영 OAuth app과 redirect URI 목록을 정리한다.
- production 비밀값의 담당자와 보관 위치를 정하되 실제 값은 저장소에 입력하지 않는다.
- 서로 다른 HMAC key 세 개를 생성·백업할 방법을 정한다.
- 장애 알림을 받을 운영 이메일을 정한다.

### Flyway V1 통합 이후, 첫 배포 직전에 할 것

- Terraform을 apply해 ECR, EC2, RDS, IAM, CloudWatch와 SSM 파라미터를 생성한다.
- Terraform output을 GitHub repository variable에 등록한다.
- production dotenv를 SSM SecureString에 입력한다.
- API DNS를 Elastic IP에 연결하고 TLS 인증서 발급이 가능한지 확인한다.
- GitHub `production` Environment를 main branch 전용으로 보호한다.
- Amplify·OAuth provider·API 도메인의 운영값을 서로 일치시킨다.

## GitHub repository variable

- `AWS_REGION`: ECR, SSM, EC2가 위치한 리전. 예: `ap-northeast-2`
- `AWS_PUBLISH_ROLE_ARN`: Backend ECR에 image를 push할 수 있는 GitHub OIDC role
- `AWS_DEPLOY_ROLE_ARN`: SSM command를 전송·조회·취소할 수 있는 GitHub OIDC role
- `ECR_REPOSITORY`: Backend ECR repository 이름
- `EC2_INSTANCE_ID`: 배포 대상 EC2 instance ID
- `SSM_BACKEND_ENV_PARAMETER`: production dotenv 전체를 담은 SecureString 파라미터
- `CLOUDWATCH_LOG_GROUP`: Docker가 사용하는 30일 보유 CloudWatch log group
- `API_DOMAIN_NAME`: scheme을 제외한 소문자 공개 API host. 예: `api.example.com`

## production dotenv

SSM SecureString value는 Docker dotenv 형식이어야 한다. 필요한 애플리케이션 key는 다음과
같다.

```dotenv
DATABASE_URL=jdbc:postgresql://<rds-endpoint>:5432/myeongro?sslmode=require
DATABASE_USERNAME=<username>
DATABASE_PASSWORD=<password>
REDIS_PASSWORD=
OPENAI_API_KEY=<key>
OPENAI_MODEL=gpt-5.6-luna
OAUTH_KAKAO_CLIENT_ID=<client-id>
OAUTH_KAKAO_CLIENT_SECRET=<client-secret>
OAUTH_KAKAO_REDIRECT_URI=https://api.example.com/login/oauth2/code/kakao
OAUTH_GOOGLE_CLIENT_ID=<client-id>
OAUTH_GOOGLE_CLIENT_SECRET=<client-secret>
OAUTH_GOOGLE_REDIRECT_URI=https://api.example.com/login/oauth2/code/google
FRONTEND_ORIGIN=https://www.example.com
SESSION_COOKIE_DOMAIN=example.com
TAROT_SELECTION_SECRET=<at-least-32-random-bytes>
TAROT_IDEMPOTENCY_SECRET=<at-least-32-random-bytes>
SAJU_IDEMPOTENCY_SECRET=<at-least-32-random-bytes>
```

`SPRING_PROFILES_ACTIVE`, `REDIS_HOST`, `REDIS_PORT`는 넣지 않는다. production Compose가 이
세 값을 소유한다.

`SESSION_COOKIE_DOMAIN`은 production 필수값이다. Amplify Frontend와 Spring API가 공유하는
상위 도메인을 사용한다. 예를 들어 Frontend가 `www.example.com`, API가
`api.example.com`이면 값은 `example.com`이다.

HMAC key 세 개는 각각 용도가 하나뿐이다. key마다 32바이트 이상의 서로 다른 난수를
생성하고 OAuth/API 비밀값과 분리해서 보관한다. 배포가 바뀌어도 같은 값을 유지해야 하며,
둘 이상의 key가 같으면 애플리케이션이 기동을 거부한다.

| Key | 전용 용도 | 회전 시 영향 |
| --- | --- | --- |
| `TAROT_SELECTION_SECRET` | 선택 슬롯에서 타로 카드 순위 결정 | 기존 타로 request ID 재시도에서 다른 카드를 뽑아 멱등성 충돌이 발생할 수 있음 |
| `TAROT_IDEMPOTENCY_SECRET` | 질문·선택지·카드를 포함한 타로 요청 지문 | 기존 타로 request ID 재시도가 멱등성 충돌로 실패함 |
| `SAJU_IDEMPOTENCY_SECRET` | 원본 출생정보·질문을 포함한 사주 요청 지문 | 기존 사주 request ID 재시도가 멱등성 충돌로 실패함 |

세 key를 백업하고, 향후 회전이 필요하면 먼저 versioned-key migration을 설계한다.

## EC2 instance role과 호스트

EC2 instance role에는 다음 권한이 필요하다.

- 지정한 SecureString을 읽는 `ssm:GetParameter`
- 해당 SecureString을 해독하는 KMS 권한
- Backend ECR image pull 권한
- Backend CloudWatch log stream 생성·기록 권한

호스트에는 SSM Agent, AWS CLI, Docker Compose plugin, curl, `flock`이 필요하다. Terraform의
user data가 이를 설치한다.

애플리케이션의 8080 포트는 loopback에만 binding한다. Terraform은 공개 80/443 포트만 열고
Elastic IP를 연결한다. 배포 bundle의 Caddy는 다음을 담당한다.

- HTTP를 HTTPS로 redirect
- `/healthz` 공개 health endpoint 제공
- 외부의 직접 `/actuator` 접근 차단
- 나머지 요청을 Docker network 내부의 API service로 전달

SSH와 공개 8080 포트는 열지 않는다. 호스트 관리는 SSM Session Manager를 사용한다.

## GitHub OIDC와 production 경계

두 workflow는 `refs/heads/main` 이외의 ref에서 실행되는 production job을 거부한다.
publish role의 trust subject는 다음과 같다.

`repo:100gammmit/MYEONGRO-Backend:ref:refs/heads/main`

저장소에 `production` GitHub Environment를 만들고 main branch만 허용한다. deploy role의
trust subject는 다음과 같다.

`repo:100gammmit/MYEONGRO-Backend:environment:production`

publish role에는 SSM 권한을 주지 않고 deploy role에는 ECR push 권한을 주지 않는다.

deploy role에 필요한 Run Command action은 다음 세 개다.

- `ssm:SendCommand`: 대상 EC2와 `AWS-RunShellScript` document에 command 전송
- `ssm:GetCommandInvocation`: 배포 결과 polling
- `ssm:CancelCommand`: workflow timeout 후 배포 중단 요청

`GetCommandInvocation`과 `CancelCommand`는 resource-level permission을 지원하지 않으므로
해당 policy statement는 `"Resource": "*"`를 사용한다. `SendCommand`는 별도 statement에서
production EC2와 document로 범위를 제한한다.

실제 권한 policy `deploy_permissions`와 trust policy `oidc_trust["deploy"]`는
`infra/terraform/iam_oidc.tf`에 정의한다. 이 문서에는 복제한 policy JSON을 두지 않는다.

외부 GitHub Action은 모두 full commit SHA로 고정한다. Dependabot이 GitHub Action update를
매주 확인한다.

## image 불변성과 rollback

ECR repository는 tag immutable이다. CI는 40자리 Git SHA tag 하나만 게시한다. 해당 tag가
이미 있으면 덮어쓰지 않고 기존 image를 재사용한다. 변경 가능한 `latest` tag는 만들지 않는다.

production Redis와 Caddy image도 읽기 쉬운 version tag와 digest를 함께 고정한다. upgrade할
때는 upstream release를 검토하고 tag와 digest를 같이 변경한 뒤 Compose와 Caddy 검증을 다시
실행한다.

배포 script는 마지막으로 정상 확인된 다음 상태를 기록한다.

- Backend image
- production env 파일
- immutable release bundle의 Compose와 Caddyfile
- AWS region
- CloudWatch log group
- API domain

새 배포의 readiness 또는 public HTTPS health check가 실패하면 마지막 성공 bundle과 실행
설정으로 image와 env를 복구한다. 이전 release state나 bundle이 불완전하면 안전한 rollback이
불가능하므로 cutover 전에 배포를 중단한다.

Database migration은 forward-only이며 배포 script가 자동 rollback하지 않는다.

## 개인정보·보유기간 출시 gate

다음 항목을 모두 설정하고 실제로 검증하기 전에는 production을 공개하면 안 된다.

- 애플리케이션과 container 로그가 회전하며 30일 이내 자동 삭제됨
- 30일이 지난 로그를 다시 조회할 수 없음을 실제 삭제 시험으로 확인함
- RDS 리전·접근 경계·저장 암호화·전송 암호화가 기록됨
- 자동 backup과 PITR 보유기간이 확정됨
- 수동 snapshot 생성 권한·목적·만료 기준이 확정됨
- backup 복원으로 되살아난 삭제 계정과 리딩을 재삭제하는 절차가 있음
- 실제 DB·backup·로그 설정이 공개 개인정보 처리방침과 일치함

Terraform은 30일 CloudWatch log group을 만들고 RDS engine 로그를 export한다. Terraform apply
때 `alarm_notification_email`을 입력하고 생성된 SNS 이메일 구독을 승인한다.

출시 전에는 애플리케이션 로그에 질문, 응답, 원본 출생정보, 이메일, OAuth subject, 원본 IP,
비밀값이 기록되지 않는지 확인한다. 운영 정책에서 요구하는 실제 만료·삭제 시험도 수행한다.

## Redis 비영속 운영

Redis는 RDB snapshot(`--save ""`)과 AOF(`--appendonly no`)를 모두 끄고 `/data`를 `tmpfs`로
mount한다. Redis가 재시작되면 로그인 session과 가입 대기 session이 만료되므로 사용자는
다시 로그인해야 한다.

과거에 `redis-data` named volume을 사용한 호스트를 upgrade할 때는 다음 순서를 따른다.

1. 모든 활성 session이 만료된다는 사실을 고지하고 기존 Redis service를 중지한다.
2. `docker compose up -d --force-recreate --renew-anon-volumes redis`로 새 Compose 구성을
   배포한다.
3. container를 검사해 `/data`가 `tmpfs`만 사용하는지 확인한다.
4. `CONFIG GET save`가 비어 있고 `CONFIG GET appendonly`가 `no`인지 확인한다.
5. 시험 key를 기록하고 Redis를 재시작한 뒤 그 key가 사라졌는지 확인한다.
6. `com.docker.compose.volume=redis-data` label을 가진 volume을 조회하고 실제 이름과 용도를
   확인한 뒤 검증된 legacy Redis volume만 제거한다.
7. 만료된 로그인·가입 대기 session이 들어 있을 수 있으므로 과거 Redis RDB/AOF volume을
   다시 연결하거나 복원하지 않는다.
