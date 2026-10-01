# 백엔드 인프라 구성(Terraform)

이 모듈은 백엔드 CI/CD가 전제로 하는 AWS 리소스를 구성한다. 대상은 EC2 호스트와
Elastic IP, private RDS PostgreSQL, ECR 저장소, CloudWatch 로그 그룹, GitHub OIDC 역할,
production 애플리케이션 비밀값을 보관하는 SSM 파라미터다. 관련 배포 코드는
`deploy/README.md`, `deploy/compose.prod.yaml`, `.github/workflows/backend-ci-cd.yaml`에 있다.

배포 bundle은 Caddy를 HTTPS reverse proxy로 실행한다. Terraform은 Elastic IP를 예약하고
80/443 포트를 열지만, 도메인 등록과 DNS A 레코드 연결은 운영자가 직접 수행한다. 이
모듈은 GitHub provider를 사용하지 않으므로 GitHub repository variable과 보호된
`production` Environment 등록도 수동 작업이다.

## 작업 시점 구분

### 지금 준비할 것 — AWS 리소스 생성 없음

- 운영에 사용할 AWS 계정과 서울 리전(`ap-northeast-2`)을 확정한다.
- Frontend 도메인과 API 도메인 이름을 정한다. 예: `www.example.com`, `api.example.com`.
- CloudWatch 경보를 받을 운영 이메일을 정한다.
- Google/Kakao 운영 앱을 어떤 계정에서 관리할지와 운영 redirect URI를 정리한다.
- 자동 backup과 PITR은 7일간 유지하고 MVP에서는 수동·final snapshot을 만들지 않는다.
- 복원은 공통 정책 저장소의 RDS backup·restore runbook에 따라 수행한다.

도메인의 실제 DNS A 레코드는 Elastic IP가 생성된 뒤에만 연결할 수 있다. 지금은 이름과
등록 주체까지만 결정하면 된다.

### 모든 기능 구현 후, RDS 생성 전에 할 것

- 출시 전 Flyway migration을 V1 기준으로 통합한다.
- 빈 PostgreSQL 16 데이터베이스에서 V1부터 전체 replay를 검증한다.
- 이 검증이 끝날 때까지 production RDS에 애플리케이션을 연결하거나 첫 migration을
  실행하지 않는다.

### 실제 배포 직전에 할 것

- Terraform state용 S3 bucket을 만들고 remote state를 초기화한다.
- `terraform plan` 결과와 월 예상 비용을 검토한다.
- 검토 후에만 `terraform apply`를 실행한다.
- apply output으로 GitHub variable, DNS, RDS 사용자, SSM 비밀값을 설정한다.
- SNS 이메일 구독과 실제 경보 수신을 확인한다.

## 최초 remote state 준비

첫 `terraform init` 전에 한 번만 수행한다. 이 root module이 자신의 state를 저장할 bucket을
동시에 만들 수 없으므로 state bucket은 모듈 밖에서 먼저 생성한다. bucket 이름이 전역에서
중복되면 다른 이름으로 바꾼다.

```sh
aws s3api create-bucket \
  --profile myeongro \
  --region ap-northeast-2 \
  --bucket myeongro-terraform-state-985950391107 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2
aws s3api put-public-access-block \
  --profile myeongro \
  --bucket myeongro-terraform-state-985950391107 \
  --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-versioning \
  --profile myeongro \
  --bucket myeongro-terraform-state-985950391107 \
  --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption \
  --profile myeongro \
  --bucket myeongro-terraform-state-985950391107 \
  --server-side-encryption-configuration \
    '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
```

`backend.production.hcl.example`을 Git에서 무시되는 `backend.production.hcl`로 복사한 뒤
bucket과 profile 값을 확인하고 초기화한다.

```sh
terraform init -backend-config=backend.production.hcl
```

S3 backend는 native lock file(`use_lockfile = true`)을 사용하므로 Terraform 1.10 이상이
필요하다. staging은 반드시 다른 state key를 사용한다. `backend.staging.hcl.example`을
`backend.staging.hcl`로 복사하고 `-var="environment=staging"`과 함께 적용한다. 그러면
환경에서 파생되는 ECR, SSM, IAM, GitHub Environment 이름이 production과 분리된다. 서로
다른 환경이 같은 backend state key를 사용하면 안 된다.

GitHub OIDC provider URL은 AWS 계정 전체에서 하나만 존재할 수 있다. 기본적으로 production
state만 이 provider를 소유한다. production apply 후 `github_oidc_provider_arn` output을
확인해 staging에 전달한다.

```sh
terraform init -reconfigure -backend-config=backend.staging.hcl
terraform apply \
  -var="environment=staging" \
  -var="github_oidc_provider_arn=<production output ARN>" \
  -var="api_domain_name=api-staging.example.com"
```

AWS 계정에 `https://token.actions.githubusercontent.com` provider가 이미 별도로 관리되고
있다면 production에도 그 ARN을 전달한다. 이 경우 Terraform은 새 provider를 만들지 않고
기존 provider를 재사용한다. 같은 provider를 두 state에 중복 import하거나 중복 관리하면
안 된다.

## 실행 방법

```sh
cd infra/terraform
terraform init -backend-config=backend.production.hcl
terraform fmt -check -recursive
terraform validate
terraform plan \
  -var="api_domain_name=api.example.com" \
  -var="alarm_notification_email=operator@example.com"
terraform apply \
  -var="api_domain_name=api.example.com" \
  -var="alarm_notification_email=operator@example.com"
```

IAM, EC2, ECR, SSM과 KMS 조회 권한을 가진 AWS 자격 증명이 필요하다. AWS provider는 현재
shell의 임의 `AWS_PROFILE`이 아니라 `var.aws_profile`에 지정된 profile을 사용한다. 기본값은
`myeongro`다. 잘못된 기본 profile로 다른 계정에 apply하는 사고를 막기 위한 설정이다.

`aws configure --profile myeongro` 또는 `aws sso login --profile myeongro`로 profile을
준비한다. 다른 이름을 사용한다면 `-var="aws_profile=<name>"`으로 덮어쓴다. apply 대상
계정도 `var.aws_account_id`와 일치해야 한다. 기본값은 현재 MYEONGRO 계정 ID이며, profile이
다른 계정을 가리키면 Terraform이 적용을 거부한다.

대상 계정과 리전에는 최소 두 개 AZ의 default subnet을 가진 default VPC가 있어야 한다.
신규 AWS 계정에는 보통 자동 생성되지만 직접 삭제했을 수 있다. `plan` 또는 `apply`에서
`data.aws_vpc.default`의 `no matching VPC found`가 발생하면 default VPC를 만들거나
(`aws ec2 create-default-vpc`) 이 모듈을 기존 VPC/subnet을 받도록 변경해야 한다.

## `terraform apply` 이후 작업

1. output을 `MYEONGRO-Backend` 저장소의 **Settings > Secrets and variables > Actions >
   Variables**에 등록한다.
   - `AWS_REGION` ← `aws_region`
   - `ECR_REPOSITORY` ← `ecr_repository`
   - `AWS_PUBLISH_ROLE_ARN` ← `aws_publish_role_arn`
   - `AWS_DEPLOY_ROLE_ARN` ← `aws_deploy_role_arn`
   - `EC2_INSTANCE_ID` ← `ec2_instance_id`
   - `SSM_BACKEND_ENV_PARAMETER` ← `ssm_backend_env_parameter`
   - `CLOUDWATCH_LOG_GROUP` ← `cloudwatch_log_group`
   - `API_DOMAIN_NAME` ← `api_domain_name`
2. 저장소에 `production` GitHub **Environment**를 만들고 `main` branch만 허용한다. deploy
   role의 trust policy도 이 Environment만 허용한다.
3. 실제 production 비밀값을 입력한다. Terraform은 SSM 파라미터를 `CHANGE_ME`로 한 번
   만들고 이후 value는 관리하지 않는다.

   ```sh
   aws ssm put-parameter \
     --region <aws_region> \
     --name <ssm_backend_env_parameter> \
     --type SecureString \
     --overwrite \
     --value "$(cat backend.env)"
   ```

   필요한 dotenv key는 `deploy/README.md`를 참고한다.
4. `api_domain_name`의 A 레코드를 `backend_public_ip`에 연결한다. 첫 Backend 배포 전에 DNS
   전파가 완료돼야 Caddy가 TLS 인증서를 발급할 수 있다.
5. `database_master_secret_arn`에서 RDS bootstrap 관리자 정보를 조회해 application/Flyway
   DB 사용자를 만든다. 애플리케이션에는 bootstrap 관리자 자격 증명을 넣지 않는다.
6. `DATABASE_URL`을
   `jdbc:postgresql://<database_endpoint>:5432/<database_name>?sslmode=require`로 설정한다.
7. `alarm_notification_email`을 입력했다면 AWS SNS 확인 이메일을 열어 구독을 승인한다.
   승인 전에는 CloudWatch 경보 알림이 전달되지 않는다.

출시 전 Flyway chain을 V1으로 통합하고 빈 PostgreSQL 16 데이터베이스에서 성공적으로
재생하기 전에는 애플리케이션을 RDS에 연결하면 안 된다. production에 baseline이 적용된
뒤부터는 모든 schema 변경을 새로운 versioned migration으로 추가한다.

## 데이터베이스·로그 정책

- RDS는 private으로 두고 Backend EC2 security group에서 오는 PostgreSQL 연결만 허용한다.
- RDS 저장소는 암호화하며 master password는 Secrets Manager에 자동 생성한다.
- 자동 backup과 PITR 보유기간은 7일이다. 운영 DB에서 삭제된 정보도 backup 만료 전까지
  잔존할 수 있으므로 공개 개인정보 처리방침과 일치시킨다.
- 개인정보가 들어 있는 production RDS는 중지하지 않는다. RDS의 backup 보유기간은
  stopped 시간을 세지 않으므로, 중지가 발생하면 공통 복구 runbook의 7일 초과 방지
  절차를 즉시 수행한다.
- deletion protection은 유지하되 MVP에서는 수동 snapshot을 만들지 않고 RDS 삭제 시
  보유기간이 정해지지 않은 final snapshot도 남기지 않는다. RDS 자체를 삭제할 때는
  연결된 automated backup도 함께 삭제한다.
- backup 복원본은 외부 연결을 차단한 새 DB에서만 검증한다. 삭제 상태를 확인할 수 없으면
  복원본을 서비스에 연결하지 않는다. 복원 요청에는 backup retention 0을 명시하고,
  24시간 안에 승인하거나 final·retained backup 없이 완전히 폐기한다.
- Backend container와 RDS engine 로그는 CloudWatch에서 30일간 보관한다.
- `alarm_notification_email`을 설정하면 SNS topic을 만들고 두 경보를 연결한 뒤 이메일
  구독을 요청한다. 운영자는 production 공개 전에 구독을 승인해야 한다.
- `alarm_notification_email`을 생략해도 경보 자체는 생성되지만 알림 대상은 없다. dry plan에는
  사용할 수 있지만 production 공개 상태로는 적합하지 않다.

## AMI 업데이트

`aws_instance.backend`는 `ec2.tf`의 `lifecycle` 설정으로 생성 이후 AMI 변경을 무시한다.
따라서 AWS가 새로운 `al2023` AMI를 게시해도 일반 `terraform apply`가 실행 중인 EC2를
자동 교체하지 않는다. 새 AMI로 의도적으로 교체할 때만 다음 명령을 실행한다.

```sh
terraform apply -replace=aws_instance.backend
```

이 명령은 EC2와 root EBS volume을 교체한다. SSM/ECR에서 복구할 수 없는 호스트 데이터는
유실된다. 렌더링된 user data가 바뀌어도 `user_data_replace_on_change = true`에 따라 EC2를
의도적으로 교체한다. 교체 후 Elastic IP 연결과 DNS가 정상화되면 Caddy가 인증서를 다시
발급한다.
