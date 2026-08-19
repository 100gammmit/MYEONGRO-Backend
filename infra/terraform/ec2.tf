data "aws_iam_policy_document" "ec2_trust" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "backend_host" {
  name               = "${var.project_name}-backend-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_trust.json
}

# Required for the SSM Agent to register the instance and receive Run Command invocations.
resource "aws_iam_role_policy_attachment" "backend_host_ssm_core" {
  role       = aws_iam_role.backend_host.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "backend_host_permissions" {
  statement {
    sid       = "ReadBackendEnvParameter"
    effect    = "Allow"
    actions   = ["ssm:GetParameter"]
    resources = ["arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:parameter${var.ssm_parameter_name}"]
  }

  statement {
    sid       = "DecryptBackendEnvParameter"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_key.ssm.arn]
  }

  statement {
    sid       = "EcrAuth"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "EcrPull"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
    ]
    resources = [aws_ecr_repository.backend.arn]
  }
}

resource "aws_iam_role_policy" "backend_host" {
  name   = "backend-deploy-access"
  role   = aws_iam_role.backend_host.id
  policy = data.aws_iam_policy_document.backend_host_permissions.json
}

resource "aws_iam_instance_profile" "backend_host" {
  name = "${var.project_name}-backend-ec2"
  role = aws_iam_role.backend_host.name
}

resource "aws_instance" "backend" {
  ami           = data.aws_ami.al2023.id
  instance_type = var.instance_type
  # sort() keeps this deterministic across applies; DescribeSubnets does not
  # guarantee a stable order, and subnet_id is ForceNew on aws_instance.
  subnet_id              = sort(data.aws_subnets.default.ids)[0]
  vpc_security_group_ids = [aws_security_group.backend.id]
  iam_instance_profile   = aws_iam_instance_profile.backend_host.name
  user_data              = file("${path.module}/templates/user_data.sh")

  associate_public_ip_address = true

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_size
    encrypted   = true
  }

  tags = {
    Name = "${var.project_name}-backend"
  }
}
