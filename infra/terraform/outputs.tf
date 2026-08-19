output "aws_region" {
  description = "GitHub repo variable: AWS_REGION"
  value       = var.aws_region
}

output "ecr_repository" {
  description = "GitHub repo variable: ECR_REPOSITORY"
  value       = aws_ecr_repository.backend.name
}

output "aws_publish_role_arn" {
  description = "GitHub repo variable: AWS_PUBLISH_ROLE_ARN"
  value       = aws_iam_role.publish.arn
}

output "aws_deploy_role_arn" {
  description = "GitHub repo variable: AWS_DEPLOY_ROLE_ARN"
  value       = aws_iam_role.deploy.arn
}

output "ec2_instance_id" {
  description = "GitHub repo variable: EC2_INSTANCE_ID"
  value       = aws_instance.backend.id
}

output "ssm_backend_env_parameter" {
  description = "GitHub repo variable: SSM_BACKEND_ENV_PARAMETER"
  value       = aws_ssm_parameter.backend_env.name
}
