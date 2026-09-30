locals {
  resource_prefix      = "${var.project_name}-${var.environment}"
  github_environment   = coalesce(var.github_environment, var.environment)
  ecr_repository_name  = coalesce(var.ecr_repository_name, "${var.project_name}-${var.environment}-backend")
  ssm_backend_env_name = coalesce(var.ssm_parameter_name, "/${var.project_name}/${var.environment}/backend/env")
  manage_github_oidc_provider = (
    var.environment == "production" && var.github_oidc_provider_arn == null
  )
  github_oidc_provider_arn = var.github_oidc_provider_arn != null ? var.github_oidc_provider_arn : try(
    aws_iam_openid_connect_provider.github[0].arn,
    null,
  )
}
