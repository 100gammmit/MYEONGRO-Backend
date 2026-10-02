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

# EC2 does not publish filesystem utilization as a default metric. Keep the
# agent configuration in Parameter Store and apply it through SSM associations
# so enabling monitoring does not replace the running instance via user_data.
resource "aws_ssm_parameter" "cloudwatch_agent_config" {
  name = "/${var.project_name}/${var.environment}/monitoring/cloudwatch-agent"
  type = "String"
  value = jsonencode({
    agent = {
      metrics_collection_interval = 60
    }
    metrics = {
      namespace = "CWAgent"
      append_dimensions = {
        InstanceId = "$${aws:InstanceId}"
      }
      aggregation_dimensions = [["InstanceId"]]
      metrics_collected = {
        disk = {
          measurement                 = ["used_percent"]
          metrics_collection_interval = 60
          resources                   = ["/"]
          drop_device                 = true
          drop_original_metrics       = ["used_percent"]
        }
      }
    }
  })
}

resource "aws_ssm_association" "cloudwatch_agent_install" {
  association_name                 = "${local.resource_prefix}-cloudwatch-agent-install"
  name                             = "AWS-ConfigureAWSPackage"
  wait_for_success_timeout_seconds = 600

  parameters = {
    action = "Install"
    name   = "AmazonCloudWatchAgent"
  }

  targets {
    key    = "InstanceIds"
    values = [aws_instance.backend.id]
  }

  depends_on = [aws_iam_role_policy.backend_host]
}

resource "aws_ssm_association" "cloudwatch_agent_configure" {
  association_name                 = "${local.resource_prefix}-cloudwatch-agent-configure"
  name                             = "AmazonCloudWatch-ManageAgent"
  wait_for_success_timeout_seconds = 600

  parameters = {
    action                        = "configure"
    mode                          = "ec2"
    optionalConfigurationSource   = "ssm"
    optionalConfigurationLocation = aws_ssm_parameter.cloudwatch_agent_config.name
    optionalRestart               = "yes"
  }

  targets {
    key    = "InstanceIds"
    values = [aws_instance.backend.id]
  }

  depends_on = [aws_ssm_association.cloudwatch_agent_install]
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
  ok_actions          = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
}

resource "aws_cloudwatch_metric_alarm" "ec2_high_cpu" {
  alarm_name          = "${local.resource_prefix}-backend-high-cpu"
  alarm_description   = "Backend EC2 CPU utilization remained at or above 80 percent for 15 minutes"
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  dimensions          = { InstanceId = aws_instance.backend.id }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 80
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
  ok_actions          = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
}

resource "aws_cloudwatch_metric_alarm" "ec2_high_disk_usage" {
  alarm_name          = "${local.resource_prefix}-backend-high-disk-usage"
  alarm_description   = "Backend EC2 root filesystem usage remained at or above 80 percent for 10 minutes"
  namespace           = "CWAgent"
  metric_name         = "disk_used_percent"
  dimensions          = { InstanceId = aws_instance.backend.id }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  threshold           = 80
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
  ok_actions          = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]

  depends_on = [aws_ssm_association.cloudwatch_agent_configure]
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
  ok_actions          = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_high_cpu" {
  alarm_name          = "${local.resource_prefix}-postgres-high-cpu"
  alarm_description   = "RDS CPU utilization remained at or above 80 percent for 15 minutes"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.postgres.identifier }
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 80
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
  ok_actions          = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_high_connections" {
  alarm_name          = "${local.resource_prefix}-postgres-high-connections"
  alarm_description   = "RDS database connections remained at or above 50 for 10 minutes"
  namespace           = "AWS/RDS"
  metric_name         = "DatabaseConnections"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.postgres.identifier }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  threshold           = 50
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
  ok_actions          = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
}

resource "aws_cloudwatch_metric_alarm" "rds_low_memory" {
  alarm_name          = "${local.resource_prefix}-postgres-low-memory"
  alarm_description   = "RDS freeable memory remained below 200 MiB for 10 minutes"
  namespace           = "AWS/RDS"
  metric_name         = "FreeableMemory"
  dimensions          = { DBInstanceIdentifier = aws_db_instance.postgres.identifier }
  statistic           = "Minimum"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  threshold           = 200 * 1024 * 1024
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
  ok_actions          = var.alarm_notification_email == null ? [] : [aws_sns_topic.production_alarms[0].arn]
}
