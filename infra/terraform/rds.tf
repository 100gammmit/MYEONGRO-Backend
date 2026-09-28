resource "aws_db_subnet_group" "backend" {
  name       = "${local.resource_prefix}-postgres"
  subnet_ids = sort(data.aws_subnets.default.ids)

  tags = {
    Name = "${local.resource_prefix}-postgres"
  }
}

resource "aws_security_group" "postgres" {
  name        = "${local.resource_prefix}-postgres"
  description = "PostgreSQL reachable only from the backend EC2 security group"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "PostgreSQL from backend EC2"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.backend.id]
  }

  egress {
    description = "Allow response traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_parameter_group" "postgres" {
  name   = "${local.resource_prefix}-postgres16"
  family = "postgres16"

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }
}

resource "aws_db_instance" "postgres" {
  identifier = "${local.resource_prefix}-postgres"

  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  db_name                     = var.db_name
  username                    = var.db_master_username
  manage_master_user_password = true
  port                        = 5432

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.backend.name
  vpc_security_group_ids = [aws_security_group.postgres.id]
  publicly_accessible    = false
  multi_az               = false

  parameter_group_name = aws_db_parameter_group.postgres.name

  backup_retention_period  = var.db_backup_retention_days
  backup_window            = "18:00-19:00"
  maintenance_window       = "sun:19:00-sun:20:00"
  copy_tags_to_snapshot    = true
  delete_automated_backups = false

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  auto_minor_version_upgrade = true
  apply_immediately          = false
  deletion_protection        = var.db_deletion_protection
  skip_final_snapshot        = var.db_skip_final_snapshot
  final_snapshot_identifier  = var.db_skip_final_snapshot ? null : "${local.resource_prefix}-postgres-final"

  depends_on = [
    aws_cloudwatch_log_group.rds_postgresql,
    aws_cloudwatch_log_group.rds_upgrade,
  ]
}
