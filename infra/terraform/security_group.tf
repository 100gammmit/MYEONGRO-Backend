# Only the public reverse-proxy ports are reachable. The application remains
# loopback-bound and host administration is through SSM Session Manager, not SSH.
resource "aws_security_group" "backend" {
  name        = "${local.resource_prefix}-backend"
  description = "Backend EC2 host: public HTTPS, SSM-managed administration"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "HTTP for ACME validation and HTTPS redirect"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Public HTTPS API"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_eip" "backend" {
  domain = "vpc"

  tags = {
    Name = "${local.resource_prefix}-backend"
  }
}

resource "aws_eip_association" "backend" {
  allocation_id = aws_eip.backend.id
  instance_id   = aws_instance.backend.id
}
