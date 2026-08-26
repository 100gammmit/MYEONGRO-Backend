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

variable "github_repository" {
  description = "GitHub \"owner/repo\" allowed to assume the OIDC roles"
  type        = string
  default     = "100gammmit/MYEONGRO-Backend"
}

variable "ecr_repository_name" {
  description = "Name of the backend ECR repository"
  type        = string
  default     = "myeongro-backend"
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
  description = "Name of the SecureString SSM parameter holding the production dotenv"
  type        = string
  default     = "/myeongro/backend/env"
}
