locals {
  ai_container_name = "ai"
  ai_log_options = {
    awslogs-group         = aws_cloudwatch_log_group.ai.name
    awslogs-region        = var.aws_region
    awslogs-stream-prefix = "ecs"
  }
}

data "aws_iam_policy_document" "ai_task_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ai_execution" {
  name               = "${local.name_prefix}-ai-execution"
  assume_role_policy = data.aws_iam_policy_document.ai_task_assume_role.json
}

resource "aws_iam_role_policy_attachment" "ai_execution" {
  role       = aws_iam_role.ai_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "ai_execution_secrets" {
  dynamic "statement" {
    for_each = var.ai_application_secret_arn == null ? [] : [1]

    content {
      sid       = "ReadAiApplicationSecret"
      effect    = "Allow"
      actions   = ["secretsmanager:GetSecretValue"]
      resources = [var.ai_application_secret_arn]
    }
  }

  dynamic "statement" {
    for_each = length(var.ai_secret_kms_key_arns) == 0 ? [] : [1]

    content {
      sid       = "DecryptCustomerManagedAiSecret"
      effect    = "Allow"
      actions   = ["kms:Decrypt"]
      resources = var.ai_secret_kms_key_arns

      condition {
        test     = "StringEquals"
        variable = "kms:ViaService"
        values   = ["secretsmanager.${var.aws_region}.${data.aws_partition.current.dns_suffix}"]
      }
    }
  }
}

resource "aws_iam_role_policy" "ai_execution_secrets" {
  count = var.ai_application_secret_arn == null && length(var.ai_secret_kms_key_arns) == 0 ? 0 : 1

  name   = "runtime-secrets"
  role   = aws_iam_role.ai_execution.id
  policy = data.aws_iam_policy_document.ai_execution_secrets.json
}

resource "aws_iam_role" "ai_task" {
  name               = "${local.name_prefix}-ai-task"
  assume_role_policy = data.aws_iam_policy_document.ai_task_assume_role.json
}

resource "aws_cloudwatch_log_group" "ai" {
  name              = "/ecs/${local.name_prefix}/ai"
  retention_in_days = 14
}

resource "aws_ecs_task_definition" "ai" {
  family                   = "${local.name_prefix}-ai"
  requires_compatibilities = ["EC2"]
  network_mode             = "bridge"
  cpu                      = "512"
  memory                   = "1664"
  execution_role_arn       = aws_iam_role.ai_execution.arn
  task_role_arn            = aws_iam_role.ai_task.arn

  container_definitions = jsonencode([{
    name              = local.ai_container_name
    image             = "${aws_ecr_repository.ai.repository_url}:${var.ai_image_tag}"
    essential         = true
    cpu               = 256
    memoryReservation = 1024
    memory            = 1536
    portMappings = [{
      name          = "http"
      containerPort = 8001
      hostPort      = 0
      protocol      = "tcp"
      appProtocol   = "http"
    }]
    environment = [
      { name = "AI_OPENAI_MODEL", value = var.ai_openai_model },
      { name = "AI_OPENAI_EMBEDDING_MODEL", value = "text-embedding-3-small" },
      { name = "AI_BACKEND_URL", value = "http://backend:8080" },
      { name = "AI_MCP_URL", value = "http://127.0.0.1:8001/mcp/" },
      { name = "AI_KAFKA_CONSUMER_ENABLED", value = "true" },
      { name = "AI_KAFKA_BOOTSTRAP_SERVERS", value = "${local.event_runtime_dns_name}:9092" },
      { name = "AI_KAFKA_CONSUMER_GROUP", value = "meetple-ai-report-analysis-v1" },
      { name = "AI_KAFKA_TOPIC", value = "meetple.moderation.report-analysis.v1" },
      { name = "AI_KAFKA_RETRY_DELAYS_SECONDS", value = "[5,30,120,600]" },
      { name = "AI_KAFKA_MAX_POLL_INTERVAL_MS", value = "900000" },
    ]
    secrets = var.ai_application_secret_arn == null ? [] : [
      {
        name      = "AI_SERVICE_TOKEN"
        valueFrom = "${var.ai_application_secret_arn}:AI_SERVICE_TOKEN::"
      },
      {
        name      = "AI_OPENAI_API_KEY"
        valueFrom = "${var.ai_application_secret_arn}:AI_OPENAI_API_KEY::"
      },
    ]
    healthCheck = {
      command     = ["CMD-SHELL", "python -c \"import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/healthz', timeout=3)\" || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
    stopTimeout = 30
    logConfiguration = {
      logDriver = "awslogs"
      options   = local.ai_log_options
    }
  }])

  tags = {
    Name = "${local.name_prefix}-ai"
  }
}

resource "aws_ecs_service" "ai" {
  name            = "${local.name_prefix}-ai"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.ai.arn
  desired_count   = var.ai_desired_count

  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100

  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.ec2.name
    base              = 0
    weight            = 100
  }

  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_private_dns_namespace.this.arn

    service {
      port_name      = "http"
      discovery_name = "ai"

      client_alias {
        dns_name = "ai"
        port     = 8001
      }
    }
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [
    aws_ecs_cluster_capacity_providers.this,
    aws_iam_role_policy.ai_execution_secrets,
  ]

  lifecycle {
    # GitHub Actions owns the image-specific active revision after the bootstrap deployment.
    ignore_changes = [task_definition]

    precondition {
      condition     = var.ai_desired_count == 0 || var.ecs_desired_capacity >= 1
      error_message = "Running the AI service requires at least one ECS container instance."
    }

    precondition {
      condition     = var.ai_desired_count == 0 || var.ai_application_secret_arn != null
      error_message = "ai_application_secret_arn is required before ai_desired_count is greater than zero."
    }

    precondition {
      condition     = var.ai_desired_count == 0 || trimspace(var.ai_openai_model) != ""
      error_message = "ai_openai_model is required before ai_desired_count is greater than zero."
    }

    precondition {
      condition     = !var.ai_integration_enabled || var.ai_desired_count >= 1
      error_message = "ai_integration_enabled requires ai_desired_count to be at least one."
    }

    precondition {
      condition     = !var.moderation_auto_warning_enabled || var.ai_integration_enabled
      error_message = "moderation_auto_warning_enabled requires ai_integration_enabled."
    }
  }

  tags = {
    Name = "${local.name_prefix}-ai"
  }
}
