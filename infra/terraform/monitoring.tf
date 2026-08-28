locals {
  monitoring_metric_namespace                    = "${title(var.project_name)}/${title(local.environment)}/Logs"
  monitoring_alarm_actions                       = [aws_sns_topic.monitoring.arn]
  rds_replication_slot_lag_alarm_threshold_bytes = var.rds_replication_slot_lag_alarm_threshold_mb * 1024 * 1024

  monitoring_infrastructure_alarms = {
    backend_running_tasks = {
      description         = "Backend ECS service has no running task."
      namespace           = "ECS/ContainerInsights"
      metric_name         = "RunningTaskCount"
      statistic           = "Minimum"
      comparison_operator = "LessThanThreshold"
      threshold           = 1
      period              = 60
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "breaching"
      dimensions = {
        ClusterName = aws_ecs_cluster.this.name
        ServiceName = aws_ecs_service.backend.name
      }
    }
    event_runtime_running_tasks = {
      description         = "Event runtime ECS service has no running task."
      namespace           = "ECS/ContainerInsights"
      metric_name         = "RunningTaskCount"
      statistic           = "Minimum"
      comparison_operator = "LessThanThreshold"
      threshold           = 1
      period              = 60
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "breaching"
      dimensions = {
        ClusterName = aws_ecs_cluster.this.name
        ServiceName = aws_ecs_service.event_runtime.name
      }
    }
    backend_cpu_high = {
      description         = "Backend ECS CPU utilization is at least 85 percent."
      namespace           = "AWS/ECS"
      metric_name         = "CPUUtilization"
      statistic           = "Average"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      threshold           = 85
      period              = 300
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "notBreaching"
      dimensions = {
        ClusterName = aws_ecs_cluster.this.name
        ServiceName = aws_ecs_service.backend.name
      }
    }
    backend_memory_high = {
      description         = "Backend ECS memory utilization is at least 85 percent."
      namespace           = "AWS/ECS"
      metric_name         = "MemoryUtilization"
      statistic           = "Average"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      threshold           = 85
      period              = 300
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "notBreaching"
      dimensions = {
        ClusterName = aws_ecs_cluster.this.name
        ServiceName = aws_ecs_service.backend.name
      }
    }
    event_runtime_cpu_high = {
      description         = "Event runtime ECS CPU utilization is at least 85 percent."
      namespace           = "AWS/ECS"
      metric_name         = "CPUUtilization"
      statistic           = "Average"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      threshold           = 85
      period              = 300
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "notBreaching"
      dimensions = {
        ClusterName = aws_ecs_cluster.this.name
        ServiceName = aws_ecs_service.event_runtime.name
      }
    }
    event_runtime_memory_high = {
      description         = "Event runtime ECS memory utilization is at least 85 percent."
      namespace           = "AWS/ECS"
      metric_name         = "MemoryUtilization"
      statistic           = "Average"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      threshold           = 85
      period              = 300
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "notBreaching"
      dimensions = {
        ClusterName = aws_ecs_cluster.this.name
        ServiceName = aws_ecs_service.event_runtime.name
      }
    }
    ecs_capacity_missing = {
      description         = "The ECS Auto Scaling group has no in-service EC2 container instance."
      namespace           = "AWS/AutoScaling"
      metric_name         = "GroupInServiceInstances"
      statistic           = "Minimum"
      comparison_operator = "LessThanThreshold"
      threshold           = 1
      period              = 60
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "breaching"
      dimensions = {
        AutoScalingGroupName = aws_autoscaling_group.ecs.name
      }
    }
    alb_unhealthy_target = {
      description         = "The application load balancer has an unhealthy backend target."
      namespace           = "AWS/ApplicationELB"
      metric_name         = "UnHealthyHostCount"
      statistic           = "Maximum"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      threshold           = 1
      period              = 60
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "notBreaching"
      dimensions = {
        LoadBalancer = aws_lb.app.arn_suffix
        TargetGroup  = aws_lb_target_group.app.arn_suffix
      }
    }
    alb_target_5xx = {
      description         = "Backend targets returned at least five HTTP 5xx responses in five minutes."
      namespace           = "AWS/ApplicationELB"
      metric_name         = "HTTPCode_Target_5XX_Count"
      statistic           = "Sum"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      threshold           = 5
      period              = 300
      evaluation_periods  = 1
      datapoints_to_alarm = 1
      treat_missing_data  = "notBreaching"
      dimensions = {
        LoadBalancer = aws_lb.app.arn_suffix
        TargetGroup  = aws_lb_target_group.app.arn_suffix
      }
    }
    rds_cpu_high = {
      description         = "RDS CPU utilization is at least 80 percent."
      namespace           = "AWS/RDS"
      metric_name         = "CPUUtilization"
      statistic           = "Average"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      threshold           = 80
      period              = 300
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "notBreaching"
      dimensions = {
        DBInstanceIdentifier = aws_db_instance.postgres.identifier
      }
    }
    rds_replication_slot_lag_high = {
      description         = "RDS oldest replication slot lag reached the configured warning threshold (${var.rds_replication_slot_lag_alarm_threshold_mb} MiB). Debezium must catch up before the slot becomes lost."
      namespace           = "AWS/RDS"
      metric_name         = "OldestReplicationSlotLag"
      statistic           = "Maximum"
      comparison_operator = "GreaterThanOrEqualToThreshold"
      threshold           = local.rds_replication_slot_lag_alarm_threshold_bytes
      period              = 60
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "notBreaching"
      dimensions = {
        DBInstanceIdentifier = aws_db_instance.postgres.identifier
      }
    }
    rds_free_storage_low = {
      description         = "RDS free storage is below 5 GiB."
      namespace           = "AWS/RDS"
      metric_name         = "FreeStorageSpace"
      statistic           = "Minimum"
      comparison_operator = "LessThanThreshold"
      threshold           = 5368709120
      period              = 300
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "breaching"
      dimensions = {
        DBInstanceIdentifier = aws_db_instance.postgres.identifier
      }
    }
    rds_freeable_memory_low = {
      description         = "RDS freeable memory is below 256 MiB."
      namespace           = "AWS/RDS"
      metric_name         = "FreeableMemory"
      statistic           = "Minimum"
      comparison_operator = "LessThanThreshold"
      threshold           = 268435456
      period              = 300
      evaluation_periods  = 2
      datapoints_to_alarm = 2
      treat_missing_data  = "notBreaching"
      dimensions = {
        DBInstanceIdentifier = aws_db_instance.postgres.identifier
      }
    }
  }

  monitoring_log_filters = {
    backend_error = {
      description    = "Spring Boot ERROR log detected."
      log_group_name = aws_cloudwatch_log_group.backend.name
      metric_name    = "BackendErrorCount"
      pattern        = "\"ERROR\""
    }
    consumer_dlq = {
      description    = "A backend Kafka consumer moved an event to a DLQ."
      log_group_name = aws_cloudwatch_log_group.backend.name
      metric_name    = "ConsumerDlqCount"
      pattern        = "\"moved to DLQ\""
    }
    debezium_failed = {
      description    = "The Debezium connector manager detected a failed connector task."
      log_group_name = aws_cloudwatch_log_group.event_runtime.name
      metric_name    = "DebeziumFailedCount"
      pattern        = "\"Debezium connector has failed tasks\""
    }
  }
}

resource "aws_sns_topic" "monitoring" {
  name = "${local.name_prefix}-monitoring"
}

resource "aws_sns_topic_subscription" "monitoring_email" {
  count = var.monitoring_notification_email == null ? 0 : 1

  topic_arn = aws_sns_topic.monitoring.arn
  protocol  = "email"
  endpoint  = var.monitoring_notification_email
}

resource "aws_cloudwatch_metric_alarm" "infrastructure" {
  for_each = local.monitoring_infrastructure_alarms

  alarm_name                = "${local.name_prefix}-${replace(each.key, "_", "-")}"
  alarm_description         = each.value.description
  actions_enabled           = var.monitoring_alarm_actions_enabled
  alarm_actions             = local.monitoring_alarm_actions
  ok_actions                = local.monitoring_alarm_actions
  insufficient_data_actions = []

  namespace           = each.value.namespace
  metric_name         = each.value.metric_name
  statistic           = each.value.statistic
  comparison_operator = each.value.comparison_operator
  threshold           = each.value.threshold
  period              = each.value.period
  evaluation_periods  = each.value.evaluation_periods
  datapoints_to_alarm = each.value.datapoints_to_alarm
  treat_missing_data  = each.value.treat_missing_data
  dimensions          = each.value.dimensions
}

resource "aws_cloudwatch_log_metric_filter" "monitoring" {
  for_each = local.monitoring_log_filters

  name           = "${local.name_prefix}-${replace(each.key, "_", "-")}"
  log_group_name = each.value.log_group_name
  pattern        = each.value.pattern

  metric_transformation {
    name          = each.value.metric_name
    namespace     = local.monitoring_metric_namespace
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_metric_alarm" "log" {
  for_each = local.monitoring_log_filters

  alarm_name                = "${local.name_prefix}-${replace(each.key, "_", "-")}"
  alarm_description         = each.value.description
  actions_enabled           = var.monitoring_alarm_actions_enabled
  alarm_actions             = local.monitoring_alarm_actions
  ok_actions                = local.monitoring_alarm_actions
  insufficient_data_actions = []

  namespace           = local.monitoring_metric_namespace
  metric_name         = each.value.metric_name
  statistic           = "Sum"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  threshold           = 1
  period              = 60
  evaluation_periods  = 1
  datapoints_to_alarm = 1
  treat_missing_data  = "notBreaching"

  depends_on = [aws_cloudwatch_log_metric_filter.monitoring]
}

resource "aws_cloudwatch_dashboard" "staging" {
  dashboard_name = "${local.name_prefix}-operations"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "ECS service CPU and memory"
          view   = "timeSeries"
          region = var.aws_region
          period = 300
          stat   = "Average"
          yAxis  = { left = { min = 0, max = 100 } }
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.this.name, "ServiceName", aws_ecs_service.backend.name, { label = "Backend CPU" }],
            [".", "MemoryUtilization", ".", ".", ".", ".", { label = "Backend memory" }],
            ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.this.name, "ServiceName", aws_ecs_service.event_runtime.name, { label = "Event runtime CPU" }],
            [".", "MemoryUtilization", ".", ".", ".", ".", { label = "Event runtime memory" }],
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "ECS running tasks and EC2 capacity"
          view   = "timeSeries"
          region = var.aws_region
          period = 60
          stat   = "Minimum"
          metrics = [
            ["ECS/ContainerInsights", "RunningTaskCount", "ClusterName", aws_ecs_cluster.this.name, "ServiceName", aws_ecs_service.backend.name, { label = "Backend running tasks" }],
            [".", ".", ".", ".", ".", aws_ecs_service.event_runtime.name, { label = "Event runtime running tasks" }],
            ["AWS/AutoScaling", "GroupInServiceInstances", "AutoScalingGroupName", aws_autoscaling_group.ecs.name, { label = "In-service EC2 instances" }],
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          title  = "ALB health and errors"
          view   = "timeSeries"
          region = var.aws_region
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "UnHealthyHostCount", "LoadBalancer", aws_lb.app.arn_suffix, "TargetGroup", aws_lb_target_group.app.arn_suffix, { label = "Unhealthy targets", stat = "Maximum" }],
            [".", "HTTPCode_Target_5XX_Count", ".", ".", ".", ".", { label = "Target 5xx", stat = "Sum" }],
            [".", "TargetResponseTime", ".", ".", ".", ".", { label = "Target response time", stat = "p95", yAxis = "right" }],
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          title  = "RDS PostgreSQL"
          view   = "timeSeries"
          region = var.aws_region
          period = 300
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.postgres.identifier, { label = "CPU %", stat = "Average" }],
            [".", "DatabaseConnections", ".", ".", { label = "Connections", stat = "Average" }],
            [".", "FreeableMemory", ".", ".", { label = "Freeable memory", stat = "Minimum", yAxis = "right" }],
            [".", "FreeStorageSpace", ".", ".", { label = "Free storage", stat = "Minimum", yAxis = "right" }],
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6
        properties = {
          title  = "RDS replication slot WAL"
          view   = "timeSeries"
          region = var.aws_region
          period = 300
          stat   = "Maximum"
          yAxis  = { left = { min = 0 } }
          annotations = {
            horizontal = [{
              label = "Replication slot warning (${var.rds_replication_slot_lag_alarm_threshold_mb} MiB)"
              value = local.rds_replication_slot_lag_alarm_threshold_bytes
              color = "#ff7f0e"
            }]
          }
          metrics = [
            ["AWS/RDS", "OldestReplicationSlotLag", "DBInstanceIdentifier", aws_db_instance.postgres.identifier, { label = "Oldest slot lag" }],
            [".", "TransactionLogsDiskUsage", ".", ".", { label = "Transaction logs disk usage" }],
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 12
        width  = 12
        height = 6
        properties = {
          title  = "Application, consumer, and Debezium failure signals"
          view   = "timeSeries"
          region = var.aws_region
          period = 60
          stat   = "Sum"
          metrics = [
            [local.monitoring_metric_namespace, "BackendErrorCount", { label = "Backend ERROR" }],
            [".", "ConsumerDlqCount", { label = "Consumer DLQ" }],
            [".", "DebeziumFailedCount", { label = "Debezium failed" }],
          ]
        }
      },
    ]
  })
}
