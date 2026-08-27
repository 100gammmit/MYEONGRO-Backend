data "aws_partition" "current" {}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }

  filter {
    name   = "default-for-az"
    values = ["true"]
  }
}

# AWS-maintained pointer to the current standard al2023 x86_64 AMI. A name-glob
# `aws_ami` lookup (e.g. "al2023-ami-*-x86_64") also matches non-standard editions
# (minimal, ecs-neuron, ...) published under the same prefix, so `most_recent`
# can silently resolve to one of those instead of the base image this module
# assumes templates/user_data.sh.tftpl is bootstrapping.
data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

data "aws_kms_key" "ssm" {
  key_id = "alias/aws/ssm"
}

# Shared by the publish role (iam_oidc.tf) and the EC2 host role (ec2.tf), both
# of which need this to authenticate to ECR -- factored out once via
# source_policy_documents so a future change (e.g. scoping the resource) can't
# be applied to one copy and forgotten on the other.
data "aws_iam_policy_document" "ecr_auth" {
  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }
}
