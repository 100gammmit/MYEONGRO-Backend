variable "aws_region" {
  description = "Region for ECR, SSM, and the EC2 instance"
  type        = string
  default     = "ap-northeast-2"
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
