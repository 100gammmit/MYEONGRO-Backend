# Backend deployment prerequisites

The production flow is GitHub Actions -> ECR -> SSM Run Command -> EC2 Docker Compose.
GitHub never reads production application secrets.

## GitHub repository variables

- `AWS_REGION`: ECR, SSM, and EC2 region (for example `ap-northeast-2`)
- `AWS_DEPLOY_ROLE_ARN`: GitHub OIDC role allowed to push ECR images and call SSM Run Command
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
TAROT_SELECTION_SECRET=<at-least-32-random-bytes>
```

Do not include `SPRING_PROFILES_ACTIVE`, `REDIS_HOST`, or `REDIS_PORT`; production Compose owns them.

## EC2 instance role and host

The EC2 instance role needs `ssm:GetParameter` for the configured SecureString and ECR pull permissions.
The host needs SSM Agent, AWS CLI, Docker with the Compose plugin, and curl. Port 8080 remains bound to loopback; the reverse proxy is expected to forward to `127.0.0.1:8080`.

The deploy script records the last healthy image and environment file. A failed readiness check restores both. Database migrations are forward-only and are not rolled back automatically.
