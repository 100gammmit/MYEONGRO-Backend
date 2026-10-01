# The GitHub token endpoint is account-global. Production owns it by default;
# other environment states receive its ARN through github_oidc_provider_arn.
resource "aws_iam_openid_connect_provider" "github" {
  count = local.manage_github_oidc_provider ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
}

# Both roles trust the same OIDC provider and differ only in which GitHub
# `sub` claim they accept — factored into one for_each'd document so a future
# trust-condition change (e.g. an added `iss` check) can't be applied to one
# role's policy and forgotten on the other.
locals {
  oidc_trust_subjects = {
    publish = "repo:${var.github_repository}:ref:refs/heads/${var.github_branch}"
    deploy  = "repo:${var.github_repository}:environment:${local.github_environment}"
  }
}

data "aws_iam_policy_document" "oidc_trust" {
  for_each = local.oidc_trust_subjects

  lifecycle {
    precondition {
      condition     = local.github_oidc_provider_arn != null
      error_message = "Non-production environments must reuse the account-level provider through github_oidc_provider_arn."
    }
  }

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = [each.value]
    }
  }
}

# --- publish role: ECR push only, restricted to the main branch ref ---

resource "aws_iam_role" "publish" {
  name               = "${local.resource_prefix}-backend-github-publish"
  assume_role_policy = data.aws_iam_policy_document.oidc_trust["publish"].json
}

data "aws_iam_policy_document" "publish_permissions" {
  source_policy_documents = [data.aws_iam_policy_document.ecr_auth.json]

  statement {
    sid    = "EcrPush"
    effect = "Allow"
    actions = [
      "ecr:DescribeImages",
      "ecr:BatchGetImage",
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
    ]
    resources = [aws_ecr_repository.backend.arn]
  }
}

resource "aws_iam_role_policy" "publish" {
  name   = "ecr-push"
  role   = aws_iam_role.publish.id
  policy = data.aws_iam_policy_document.publish_permissions.json
}

# --- deploy role: SSM Run Command only, restricted to the protected production environment ---

resource "aws_iam_role" "deploy" {
  name               = "${local.resource_prefix}-backend-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.oidc_trust["deploy"].json
}

# deploy/README.md points here as the source of truth for this policy rather
# than embedding its own copy -- see that file's "GitHub OIDC and production
# boundary" section.
data "aws_iam_policy_document" "deploy_permissions" {
  statement {
    sid     = "SendBackendDeployCommand"
    effect  = "Allow"
    actions = ["ssm:SendCommand"]
    resources = [
      aws_instance.backend.arn,
      "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}::document/AWS-RunShellScript",
    ]
  }

  statement {
    sid    = "InspectAndCancelBackendDeployCommand"
    effect = "Allow"
    actions = [
      "ssm:GetCommandInvocation",
      "ssm:CancelCommand",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "ssm-deploy"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy_permissions.json
}
