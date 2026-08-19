# No inbound rules: the app port stays loopback-bound (see compose.prod.yaml) and
# host access is via SSM Session Manager, not SSH.
resource "aws_security_group" "backend" {
  name        = "${var.project_name}-backend"
  description = "Backend EC2 host: no inbound, SSM-managed access only"
  vpc_id      = data.aws_vpc.default.id

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
