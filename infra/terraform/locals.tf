locals {
  resource_prefix      = "${var.project_name}-${var.environment}"
  github_environment   = coalesce(var.github_environment, var.environment)
  ecr_repository_name  = coalesce(var.ecr_repository_name, "${var.project_name}-${var.environment}-backend")
  ssm_backend_env_name = coalesce(var.ssm_parameter_name, "/${var.project_name}/${var.environment}/backend/env")
}
