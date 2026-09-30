resource "aws_cloudwatch_log_group" "backend" {
  name              = "/${var.project_name}/${var.environment}/backend"
  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "rds_postgresql" {
  name              = "/aws/rds/instance/${local.resource_prefix}-postgres/postgresql"
  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "rds_upgrade" {
  name              = "/aws/rds/instance/${local.resource_prefix}-postgres/upgrade"
  retention_in_days = 30
}

resource "aws_sns_topic" "production_alarms" {
  count = var.alarm_notification_email == null ? 0 : 1
  name  = "${local.resource_prefix}-alarms"
}

resource "aws_sns_topic_subscription" "production_alarm_email" {
  count     = var.alarm_notification_email == null ? 0 : 1
  topic_arn = aws_sns_topic.production_alarms[0].arn
  protocol  = "email"
  endpoint  = var.alarm_notification_email
}

resource "aws_cloudwatch_metric_alarm" "ec2_status_check" {
  alarm_name          = "${local.resource_prefix}-backend-status-check"
  alarm_description   = "Backend EC2 instance or host status check failed"
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed"
  dimensions          = { InstanceId = aws_instance.backend.id }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_free_storage" {
  alarm_name          = "${local.resource_prefix}-postgres-low-storage"
  alarm_description   = "RDS free storage dropped below 5 GiB"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.postgres.identifier }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 2
  threshold           = 5 * 1024 * 1024 * 1024
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
}
