# Backend infrastructure (Terraform)

Provisions the AWS resources that the backend's CI/CD pipeline (`deploy/README.md`,
`compose.prod.yaml`, `.github/workflows/backend-ci-cd.yaml` — currently on the `dev` branch,
not yet merged to `main`) assumes already exist: the EC2 host, ECR repository, GitHub OIDC
roles, and the SSM parameter that carries production secrets. State is local
(`terraform.tfstate`, gitignored) — this has not been migrated to a remote backend yet.

Out of scope: reverse proxy / TLS / domain, RDS or any other database, and registering the
GitHub repository variables or the `production` environment itself (GitHub has no Terraform
provider configured here — do that by hand using the outputs below).

## Usage

```sh
cd infra/terraform
terraform init
terraform plan
terraform apply
```

Requires AWS credentials with sufficient privileges (IAM, EC2, ECR, SSM, KMS read). The
provider authenticates as the named profile in `var.aws_profile` (default `myeongro`), not
whatever `AWS_PROFILE` happens to be set to — this is deliberate, so a stray default AWS
profile in your shell can't cause `apply` to silently run against the wrong account. Configure
that profile (`aws configure --profile myeongro` or `aws sso login --profile myeongro`), or
override it with `-var="aws_profile=<name>"` if you use a different profile name.

## After `terraform apply`

1. Copy the outputs into the `MYEONGRO-Backend` GitHub repo's **Settings > Secrets and
   variables > Actions > Variables**:
   - `AWS_REGION` <- `aws_region`
   - `ECR_REPOSITORY` <- `ecr_repository`
   - `AWS_PUBLISH_ROLE_ARN` <- `aws_publish_role_arn`
   - `AWS_DEPLOY_ROLE_ARN` <- `aws_deploy_role_arn`
   - `EC2_INSTANCE_ID` <- `ec2_instance_id`
   - `SSM_BACKEND_ENV_PARAMETER` <- `ssm_backend_env_parameter`
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
4. Point `DATABASE_URL` in that dotenv at a Postgres instance (RDS or otherwise) — this
   module does not provision one.
