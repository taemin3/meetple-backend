data "aws_partition" "current" {}

data "aws_caller_identity" "current" {}

data "aws_secretsmanager_secret" "rds_master" {
  arn = aws_db_instance.postgres.master_user_secret[0].secret_arn
}

locals {
  event_runtime_service_arn             = "arn:${data.aws_partition.current.partition}:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/${aws_ecs_cluster.this.name}/${aws_ecs_service.event_runtime.name}"
  backend_service_arn                   = "arn:${data.aws_partition.current.partition}:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/${aws_ecs_cluster.this.name}/${aws_ecs_service.backend.name}"
  event_runtime_redeploy_automation_arn = "arn:${data.aws_partition.current.partition}:ssm:${var.aws_region}:${data.aws_caller_identity.current.account_id}:automation-definition/${aws_ssm_document.event_runtime_redeploy.name}"
}

data "aws_iam_policy_document" "event_runtime_redeploy_automation_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ssm.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "event_runtime_redeploy_automation" {
  name               = "${local.name_prefix}-event-runtime-redeploy-automation"
  assume_role_policy = data.aws_iam_policy_document.event_runtime_redeploy_automation_assume_role.json
}

data "aws_iam_policy_document" "event_runtime_redeploy_automation" {
  statement {
    effect  = "Allow"
    actions = ["ecs:UpdateService"]
    resources = [
      local.event_runtime_service_arn,
      local.backend_service_arn,
    ]
  }
}

resource "aws_iam_role_policy" "event_runtime_redeploy_automation" {
  name   = "force-event-runtime-deployment"
  role   = aws_iam_role.event_runtime_redeploy_automation.id
  policy = data.aws_iam_policy_document.event_runtime_redeploy_automation.json
}

resource "aws_ssm_document" "event_runtime_redeploy" {
  name            = "${local.name_prefix}-redeploy-event-runtime"
  document_type   = "Automation"
  document_format = "JSON"

  content = jsonencode({
    schemaVersion = "0.3"
    description   = "Force an ECS service deployment so a new task reads the current secret value"
    assumeRole    = "{{ AutomationAssumeRole }}"
    parameters = {
      AutomationAssumeRole = {
        type        = "AWS::IAM::Role::Arn"
        description = "Role assumed by Systems Manager Automation"
      }
      ClusterName = {
        type        = "String"
        description = "ECS cluster name"
      }
      ServiceName = {
        type        = "String"
        description = "ECS service name"
      }
    }
    mainSteps = [{
      name   = "forceNewDeployment"
      action = "aws:executeAwsApi"
      inputs = {
        Service            = "ecs"
        Api                = "UpdateService"
        cluster            = "{{ ClusterName }}"
        service            = "{{ ServiceName }}"
        forceNewDeployment = true
      }
      isEnd = true
    }]
  })

  tags = {
    Name = "${local.name_prefix}-redeploy-event-runtime"
  }
}

data "aws_iam_policy_document" "event_runtime_redeploy_event_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "event_runtime_redeploy_event" {
  name               = "${local.name_prefix}-event-runtime-redeploy-event"
  assume_role_policy = data.aws_iam_policy_document.event_runtime_redeploy_event_assume_role.json
}

data "aws_iam_policy_document" "event_runtime_redeploy_event" {
  statement {
    effect  = "Allow"
    actions = ["ssm:StartAutomationExecution"]
    resources = [
      local.event_runtime_redeploy_automation_arn,
      "${local.event_runtime_redeploy_automation_arn}:*",
    ]
  }

  statement {
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.event_runtime_redeploy_automation.arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ssm.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "event_runtime_redeploy_event" {
  name   = "start-event-runtime-redeploy"
  role   = aws_iam_role.event_runtime_redeploy_event.id
  policy = data.aws_iam_policy_document.event_runtime_redeploy_event.json
}

resource "aws_cloudwatch_event_rule" "rds_master_secret_rotated" {
  name        = "${local.name_prefix}-rds-secret-rotated"
  description = "Redeploy RDS secret consumers when the master secret AWSCURRENT version changes"

  event_pattern = jsonencode({
    source      = ["aws.secretsmanager"]
    detail-type = ["Secret Label Updated"]
    detail = {
      name         = [data.aws_secretsmanager_secret.rds_master.name]
      labelUpdated = ["AWSCURRENT"]
    }
  })
}

resource "aws_cloudwatch_event_rule" "backend_secrets_rotated" {
  name        = "${local.name_prefix}-backend-secret-rotated"
  description = "Redeploy the backend when an application secret AWSCURRENT version changes"

  event_pattern = jsonencode({
    source      = ["aws.secretsmanager"]
    detail-type = ["Secret Label Updated"]
    detail = {
      name = [
        data.aws_secretsmanager_secret.backend_application.name,
        data.aws_secretsmanager_secret.firebase_credentials.name,
      ]
      labelUpdated = ["AWSCURRENT"]
    }
  })
}

resource "aws_cloudwatch_event_target" "event_runtime_redeploy" {
  rule      = aws_cloudwatch_event_rule.rds_master_secret_rotated.name
  target_id = "RedeployEventRuntime"
  arn       = local.event_runtime_redeploy_automation_arn
  role_arn  = aws_iam_role.event_runtime_redeploy_event.arn

  input = jsonencode({
    AutomationAssumeRole = [aws_iam_role.event_runtime_redeploy_automation.arn]
    ClusterName          = [aws_ecs_cluster.this.name]
    ServiceName          = [aws_ecs_service.event_runtime.name]
  })

  retry_policy {
    maximum_event_age_in_seconds = 3600
    maximum_retry_attempts       = 3
  }

  depends_on = [
    aws_iam_role_policy.event_runtime_redeploy_automation,
    aws_iam_role_policy.event_runtime_redeploy_event,
  ]
}

resource "aws_cloudwatch_event_target" "backend_redeploy_on_rds_secret" {
  rule      = aws_cloudwatch_event_rule.rds_master_secret_rotated.name
  target_id = "RedeployBackend"
  arn       = local.event_runtime_redeploy_automation_arn
  role_arn  = aws_iam_role.event_runtime_redeploy_event.arn

  input = jsonencode({
    AutomationAssumeRole = [aws_iam_role.event_runtime_redeploy_automation.arn]
    ClusterName          = [aws_ecs_cluster.this.name]
    ServiceName          = [aws_ecs_service.backend.name]
  })

  retry_policy {
    maximum_event_age_in_seconds = 3600
    maximum_retry_attempts       = 3
  }

  depends_on = [
    aws_iam_role_policy.event_runtime_redeploy_automation,
    aws_iam_role_policy.event_runtime_redeploy_event,
  ]
}

resource "aws_cloudwatch_event_target" "backend_redeploy_on_application_secret" {
  rule      = aws_cloudwatch_event_rule.backend_secrets_rotated.name
  target_id = "RedeployBackend"
  arn       = local.event_runtime_redeploy_automation_arn
  role_arn  = aws_iam_role.event_runtime_redeploy_event.arn

  input = jsonencode({
    AutomationAssumeRole = [aws_iam_role.event_runtime_redeploy_automation.arn]
    ClusterName          = [aws_ecs_cluster.this.name]
    ServiceName          = [aws_ecs_service.backend.name]
  })

  retry_policy {
    maximum_event_age_in_seconds = 3600
    maximum_retry_attempts       = 3
  }

  depends_on = [
    aws_iam_role_policy.event_runtime_redeploy_automation,
    aws_iam_role_policy.event_runtime_redeploy_event,
  ]
}
