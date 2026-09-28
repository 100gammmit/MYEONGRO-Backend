variable "aws_region" {
  description = "Region for ECR, SSM, and the EC2 instance"
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_profile" {
  description = "Named AWS CLI profile the provider authenticates as. Set explicitly (rather than left to AWS_PROFILE/the default profile) so a misconfigured shell can't silently apply against the wrong AWS account."
  type        = string
  default     = "myeongro"
}

variable "aws_account_id" {
  description = "Expected AWS account ID. Belt-and-suspenders alongside var.aws_profile: if that profile is ever repointed at a different account (re-run of aws sso login, rotated/misconfigured credentials), apply refuses to run instead of silently changing the wrong account's infrastructure."
  type        = string
  default     = "985950391107"
}

variable "project_name" {
  description = "Prefix used for resource names and tags"
  type        = string
  default     = "myeongro"
}

variable "environment" {
  description = "Deployment environment used in resource names and tags"
  type        = string
  default     = "production"

  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "environment must be staging or production."
  }
}

variable "github_repository" {
  description = "GitHub \"owner/repo\" allowed to assume the OIDC roles"
  type        = string
  default     = "100gammmit/MYEONGRO-Backend"
}

variable "github_oidc_provider_arn" {
  description = "Existing account-level GitHub Actions OIDC provider ARN; required outside the production state that owns it"
  type        = string
  default     = null
  nullable    = true
}

variable "github_branch" {
  description = "GitHub branch allowed to publish backend images"
  type        = string
  default     = "main"
}

variable "github_environment" {
  description = "Protected GitHub Environment allowed to deploy the backend; defaults to var.environment"
  type        = string
  default     = null
  nullable    = true
}

variable "ecr_repository_name" {
  description = "Name of the backend ECR repository; defaults to <project>-<environment>-backend"
  type        = string
  default     = null
  nullable    = true
}

variable "instance_type" {
  description = "EC2 instance type for the backend host"
  type        = string
  default     = "t3.small"
}

variable "root_volume_size" {
  description = "Root EBS volume size in GB"
  type        = number
  default     = 20
}

variable "ssm_parameter_name" {
  description = "Name of the SecureString SSM parameter holding the environment dotenv; defaults to /<project>/<environment>/backend/env"
  type        = string
  default     = null
  nullable    = true
}

variable "api_domain_name" {
  description = "Public DNS name terminated by Caddy on the backend host, without scheme"
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$", var.api_domain_name))
    error_message = "api_domain_name must be a lowercase fully-qualified domain name such as api.example.com."
  }
}

variable "db_name" {
  description = "Initial PostgreSQL database name"
  type        = string
  default     = "myeongro"
}

variable "db_master_username" {
  description = "RDS bootstrap administrator name; the password is generated and stored by RDS in Secrets Manager"
  type        = string
  default     = "myeongro_admin"
}

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Initial RDS gp3 storage in GiB"
  type        = number
  default     = 20

  validation {
    condition     = var.db_allocated_storage >= 20
    error_message = "db_allocated_storage must be at least 20 GiB."
  }
}

variable "db_backup_retention_days" {
  description = "Automated backup and point-in-time recovery retention in days"
  type        = number
  default     = 7

  validation {
    condition     = var.db_backup_retention_days >= 1 && var.db_backup_retention_days <= 35
    error_message = "db_backup_retention_days must be between 1 and 35."
  }
}

variable "db_deletion_protection" {
  description = "Protect the production RDS instance from accidental deletion"
  type        = bool
  default     = true
}

variable "db_skip_final_snapshot" {
  description = "Skip the final RDS snapshot when the instance is deleted; keep false in production"
  type        = bool
  default     = false
}

variable "alarm_notification_email" {
  description = "Operator email subscribed to production alarms; null creates alarms without notifications"
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.alarm_notification_email == null || can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.alarm_notification_email))
    error_message = "alarm_notification_email must be null or a valid email address."
  }
}
