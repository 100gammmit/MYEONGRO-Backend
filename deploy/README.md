# Backend deployment prerequisites

The production flow is GitHub Actions -> ECR -> SSM Run Command -> EC2 Docker Compose.
GitHub never reads production application secrets.

## GitHub repository variables

- `AWS_REGION`: ECR, SSM, and EC2 region (for example `ap-northeast-2`)
- `AWS_PUBLISH_ROLE_ARN`: GitHub OIDC role allowed to push images only to the backend ECR repository
- `AWS_DEPLOY_ROLE_ARN`: GitHub OIDC role allowed to send, inspect, and cancel SSM commands only
- `ECR_REPOSITORY`: backend ECR repository name
- `EC2_INSTANCE_ID`: target EC2 instance ID
- `SSM_BACKEND_ENV_PARAMETER`: one SecureString parameter containing the production dotenv file

The SecureString value must use Docker dotenv syntax. Required application keys are:

```dotenv
DATABASE_URL=jdbc:postgresql://<rds-endpoint>:5432/myeongro
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
```

Do not include `SPRING_PROFILES_ACTIVE`, `REDIS_HOST`, or `REDIS_PORT`; production Compose owns them.
`SESSION_COOKIE_DOMAIN` is required in production and must be the shared parent of the Amplify frontend
and Spring API hosts. For example, use `example.com` for `www.example.com` and `api.example.com`.

## EC2 instance role and host

The EC2 instance role needs `ssm:GetParameter` for the configured SecureString and ECR pull permissions.
The host needs SSM Agent, AWS CLI, Docker with the Compose plugin, curl, and `flock`. Port 8080 remains bound to loopback; the reverse proxy is expected to forward to `127.0.0.1:8080`.

## GitHub OIDC and production boundary

Both workflows reject any ref other than `refs/heads/main`. Configure the publish role trust subject for
`repo:100gammmit/MYEONGRO-Backend:ref:refs/heads/main`.

Create and protect a GitHub Environment named `production`. The deploy role trust subject must be
`repo:100gammmit/MYEONGRO-Backend:environment:production`. Restrict that environment to the `main`
branch. Do not grant SSM permissions to the publish role or ECR push permissions to the deploy role.

The deploy role requires exactly these Run Command actions:

- `ssm:SendCommand` for the target EC2 instance and the `AWS-RunShellScript` document
- `ssm:GetCommandInvocation` to poll the deployment result
- `ssm:CancelCommand` to stop a deployment after the workflow timeout

`GetCommandInvocation` and `CancelCommand` do not support resource-level permissions, so their
policy statement must use `"Resource": "*"`. Keep `SendCommand` in a separate statement scoped to
the production instance and document. For example, replace the placeholders in this deploy-role
permissions policy:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SendBackendDeployCommand",
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:<region>:<account-id>:instance/<instance-id>",
        "arn:aws:ssm:<region>::document/AWS-RunShellScript"
      ]
    },
    {
      "Sid": "InspectAndCancelBackendDeployCommand",
      "Effect": "Allow",
      "Action": [
        "ssm:GetCommandInvocation",
        "ssm:CancelCommand"
      ],
      "Resource": "*"
    }
  ]
}
```

The deploy role trust policy must also limit GitHub OIDC to the protected production environment:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::<account-id>:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:100gammmit/MYEONGRO-Backend:environment:production"
        }
      }
    }
  ]
}
```

All third-party Actions are pinned to full commit SHAs. Dependabot checks GitHub Actions updates weekly.

The deploy script records the last healthy image and environment file. A failed readiness check restores both. Database migrations are forward-only and are not rolled back automatically.
