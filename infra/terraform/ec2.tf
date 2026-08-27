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
  source_policy_documents = [data.aws_iam_policy_document.ecr_auth.json]

  statement {
    sid       = "ReadBackendEnvParameter"
    effect    = "Allow"
    actions   = ["ssm:GetParameter"]
    resources = [aws_ssm_parameter.backend_env.arn]
  }

  statement {
    sid       = "DecryptBackendEnvParameter"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_key.ssm.arn]
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
  ami           = data.aws_ssm_parameter.al2023_ami.value
  instance_type = var.instance_type
  # sort() keeps this deterministic across applies; DescribeSubnets does not
  # guarantee a stable order, and subnet_id is ForceNew on aws_instance.
  subnet_id              = sort(data.aws_subnets.default.ids)[0]
  vpc_security_group_ids = [aws_security_group.backend.id]
  iam_instance_profile   = aws_iam_instance_profile.backend_host.name
  user_data = templatefile("${path.module}/templates/user_data.sh.tftpl", {
    paths_sh = file("${path.module}/../../deploy/lib/paths.sh")
  })

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

  # AWS republishes a new al2023 AMI regularly; without this, a plain `terraform
  # apply` after one ships would destroy/recreate this instance for an AMI bump
  # nobody asked for. Replace deliberately (taint/apply with a new default) when
  # you actually want to move to a newer AMI.
  lifecycle {
    ignore_changes = [ami]
  }
}
