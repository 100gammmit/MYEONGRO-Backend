# Backend infrastructure (Terraform)

Provisions the AWS resources that the backend's CI/CD pipeline (`deploy/README.md`,
`compose.prod.yaml`, `.github/workflows/backend-ci-cd.yaml`) assumes already exist: the EC2 host
and Elastic IP, private RDS PostgreSQL, ECR repository, CloudWatch log groups, GitHub OIDC roles,
and the SSM parameter that carries production application secrets.

The deployment bundle runs Caddy as the HTTPS reverse proxy. Terraform reserves the Elastic IP
and opens ports 80/443, but domain registration and the DNS A record remain operator tasks.
Registering GitHub repository variables and the protected `production` environment is also a
manual task because this module intentionally has no GitHub provider.

## One-time remote state bootstrap

Do this before the first `terraform init`. The state bucket is deliberately bootstrapped outside
this root module so Terraform never tries to create the bucket in which its own state must already
exist. Replace the bucket name if it is not globally unique:

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

Copy `backend.production.hcl.example` to the gitignored `backend.production.hcl`, update its
bucket/profile values, and initialize with:

```sh
terraform init -backend-config=backend.production.hcl
```

The S3 backend uses native lock files (`use_lockfile = true`) and requires Terraform 1.10+.
Staging must use a different state key: copy `backend.staging.hcl.example` to
`backend.staging.hcl` and also apply with `-var="environment=staging"`. Environment-derived ECR,
SSM, IAM, and GitHub Environment names then stay separate from production. Never reuse a backend
state key between environments.

The GitHub OIDC provider URL is account-global, so the production state is its sole owner by
default. After production apply, read the `github_oidc_provider_arn` output and pass it to staging:

```sh
terraform init -reconfigure -backend-config=backend.staging.hcl
terraform apply \
  -var="environment=staging" \
  -var="github_oidc_provider_arn=<production output ARN>" \
  -var="api_domain_name=api-staging.example.com"
```

If the AWS account already has a separately managed provider for
`https://token.actions.githubusercontent.com`, pass its ARN to production too; Terraform then
reuses it instead of creating another. Do not import or manage the same provider in both states.

## Usage

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

Requires AWS credentials with sufficient privileges (IAM, EC2, ECR, SSM, KMS read). The
provider authenticates as the named profile in `var.aws_profile` (default `myeongro`), not
whatever `AWS_PROFILE` happens to be set to — this is deliberate, so a stray default AWS
profile in your shell can't cause `apply` to silently run against the wrong account. Configure
that profile (`aws configure --profile myeongro` or `aws sso login --profile myeongro`), or
override it with `-var="aws_profile=<name>"` if you use a different profile name. `apply`
also refuses to run against any account other than `var.aws_account_id` (default the
`myeongro` account), so a repointed/misconfigured profile fails loudly instead of silently
changing the wrong account's infrastructure.

The target account/region must have a default VPC with default subnets in at least two AZs — AWS creates one automatically for new
accounts/regions unless it was deliberately deleted. If `plan`/`apply` fails on
`data.aws_vpc.default` with "no matching VPC found", create one (`aws ec2
create-default-vpc`) or point this module at a different, existing VPC/subnets instead.

## After `terraform apply`

1. Copy the outputs into the `MYEONGRO-Backend` GitHub repo's **Settings > Secrets and
   variables > Actions > Variables**:
   - `AWS_REGION` <- `aws_region`
   - `ECR_REPOSITORY` <- `ecr_repository`
   - `AWS_PUBLISH_ROLE_ARN` <- `aws_publish_role_arn`
   - `AWS_DEPLOY_ROLE_ARN` <- `aws_deploy_role_arn`
   - `EC2_INSTANCE_ID` <- `ec2_instance_id`
   - `SSM_BACKEND_ENV_PARAMETER` <- `ssm_backend_env_parameter`
   - `CLOUDWATCH_LOG_GROUP` <- `cloudwatch_log_group`
   - `API_DOMAIN_NAME` <- `api_domain_name`
2. Create a GitHub **Environment** named `production` on the repo and restrict it to the
   `main` branch (the deploy role's trust policy only allows that environment).
3. Fill in the real production secrets — Terraform only creates the SSM parameter with a
   `CHANGE_ME` placeholder and never touches its value again:
   ```sh
   aws ssm put-parameter \
     --region <aws_region> \
     --name <ssm_backend_env_parameter> \
     --type SecureString \
     --overwrite \
     --value "$(cat backend.env)"
   ```
   See `deploy/README.md` (on the `dev` branch) for the required dotenv keys.
4. Create an A record for `api_domain_name` pointing at `backend_public_ip`. Wait for DNS to
   resolve before the first backend deployment so Caddy can obtain its TLS certificate.
5. Retrieve the generated RDS bootstrap administrator from `database_master_secret_arn`, create
   the application/Flyway database owner, and place that application user's credentials in the
   SSM dotenv. Do not put the RDS bootstrap administrator in the application environment.
6. Set `DATABASE_URL` to
   `jdbc:postgresql://<database_endpoint>:5432/<database_name>?sslmode=require`.
7. If `alarm_notification_email` was supplied, open the AWS SNS confirmation email and confirm
   the subscription. Until it is confirmed, CloudWatch alarm notifications are not delivered.

Do not start the application against RDS until the pre-release Flyway chain has been consolidated
and replayed successfully on an empty PostgreSQL 16 database. Once production has applied the
baseline, future schema changes must be new versioned migrations.

## Database and log policy

- RDS is private and accepts PostgreSQL only from the backend EC2 security group.
- RDS storage is encrypted and the master password is generated into Secrets Manager.
- Automated backup/PITR retention defaults to 7 days; confirm this matches the published policy.
- Deletion protection is enabled and a final snapshot is required by default.
- Backend container and RDS engine log groups retain data for 30 days.
- Setting `alarm_notification_email` creates an SNS topic, wires both alarms to it, and requests
  an email subscription. The operator must confirm that subscription before production launch.
- If `alarm_notification_email` is omitted, the alarms still exist but have no notification action;
  this is suitable for a dry plan, not a production launch.

## AMI updates

`aws_instance.backend` ignores AMI changes after creation (see the `lifecycle` block in
`ec2.tf`) so a routine `apply` after AWS republishes the `al2023` AMI doesn't
destroy/recreate the running instance. To deliberately move to a newer AMI, run:
```sh
terraform apply -replace=aws_instance.backend
```
This replaces the instance (new EBS root volume, loses anything not recoverable from SSM/ECR) and
re-runs `templates/user_data.sh.tftpl` from scratch. Changes to rendered user data also replace the
instance intentionally (`user_data_replace_on_change = true`). Caddy certificates are reacquired
after DNS resolves to the replacement Elastic IP association.
