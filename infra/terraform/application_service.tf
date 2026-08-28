locals {
  backend_container_name = "backend"
  backend_application_secret_keys = [
    "JWT_SECRET",
    "MAIL_HOST",
    "MAIL_USERNAME",
    "MAIL_PASSWORD",
    "EMAIL_FROM_ADDRESS",
    "NAVER_LOCATION_CLIENT_ID",
    "NAVER_LOCATION_CLIENT_SECRET",
    "NAVER_MAPS_CLIENT_ID",
    "NAVER_MAPS_CLIENT_SECRET",
  ]
  backend_log_options = {
    awslogs-group         = aws_cloudwatch_log_group.backend.name
    awslogs-region        = var.aws_region
    awslogs-stream-prefix = "ecs"
  }
}

data "aws_secretsmanager_secret" "backend_application" {
  arn = var.backend_application_secret_arn
}

data "aws_secretsmanager_secret" "firebase_credentials" {
  arn = var.firebase_credentials_secret_arn
}

data "aws_iam_policy_document" "backend_task_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "backend_execution" {
  name               = "${local.name_prefix}-backend-execution"
  assume_role_policy = data.aws_iam_policy_document.backend_task_assume_role.json
}

resource "aws_iam_role_policy_attachment" "backend_execution" {
  role       = aws_iam_role.backend_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "backend_execution_secrets" {
  statement {
    effect  = "Allow"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      aws_db_instance.postgres.master_user_secret[0].secret_arn,
      data.aws_secretsmanager_secret.backend_application.arn,
      data.aws_secretsmanager_secret.firebase_credentials.arn,
    ]
  }

  dynamic "statement" {
    for_each = length(var.backend_secret_kms_key_arns) == 0 ? [] : [1]

    content {
      sid       = "DecryptCustomerManagedSecrets"
      effect    = "Allow"
      actions   = ["kms:Decrypt"]
      resources = var.backend_secret_kms_key_arns

      condition {
        test     = "StringEquals"
        variable = "kms:ViaService"
        values   = ["secretsmanager.${var.aws_region}.${data.aws_partition.current.dns_suffix}"]
      }
    }
  }
}

resource "aws_iam_role_policy" "backend_execution_secrets" {
  name   = "runtime-secrets"
  role   = aws_iam_role.backend_execution.id
  policy = data.aws_iam_policy_document.backend_execution_secrets.json
}

resource "aws_iam_role" "backend_task" {
  name               = "${local.name_prefix}-backend-task"
  assume_role_policy = data.aws_iam_policy_document.backend_task_assume_role.json
}

data "aws_iam_policy_document" "backend_images" {
  statement {
    sid       = "ListImageBucket"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.images.arn]
  }

  statement {
    sid    = "ManageImageObjects"
    effect = "Allow"
    actions = [
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:PutObject",
    ]
    resources = ["${aws_s3_bucket.images.arn}/*"]
  }

  statement {
    sid       = "InvalidateDeletedImages"
    effect    = "Allow"
    actions   = ["cloudfront:CreateInvalidation"]
    resources = [aws_cloudfront_distribution.images.arn]
  }
}

resource "aws_iam_role_policy" "backend_images" {
  name   = "image-storage"
  role   = aws_iam_role.backend_task.id
  policy = data.aws_iam_policy_document.backend_images.json
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name_prefix}-backend"
  requires_compatibilities = ["EC2"]
  network_mode             = "bridge"
  execution_role_arn       = aws_iam_role.backend_execution.arn
  task_role_arn            = aws_iam_role.backend_task.arn

  container_definitions = jsonencode([{
    name              = local.backend_container_name
    image             = "${aws_ecr_repository.backend.repository_url}:${var.backend_image_tag}"
    essential         = true
    cpu               = 512
    memoryReservation = 1024
    memory            = 1536
    portMappings = [{
      name          = "http"
      containerPort = 8080
      hostPort      = 0
      protocol      = "tcp"
    }]
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/${var.db_name}" },
      { name = "SPRING_DATA_REDIS_HOST", value = local.event_runtime_dns_name },
      { name = "SPRING_DATA_REDIS_PORT", value = "6379" },
      { name = "KAFKA_BOOTSTRAP_SERVERS", value = "${local.event_runtime_dns_name}:9092" },
      { name = "KAFKA_CONSUMER_CONCURRENCY", value = tostring(var.kafka_consumer_concurrency) },
      { name = "PUSH_KAFKA_CONSUMER_ENABLED", value = "true" },
      { name = "PUSH_FCM_ENABLED", value = "true" },
      { name = "EMAIL_DELIVERY_KAFKA_CONSUMER_ENABLED", value = "true" },
      { name = "IMAGE_DELETION_KAFKA_CONSUMER_ENABLED", value = "true" },
      { name = "IMAGE_STORAGE_BUCKET", value = aws_s3_bucket.images.id },
      { name = "IMAGE_STORAGE_REGION", value = var.aws_region },
      { name = "IMAGE_STORAGE_PUBLIC_BASE_URL", value = "https://${aws_cloudfront_distribution.images.domain_name}" },
      { name = "IMAGE_STORAGE_CLOUDFRONT_DISTRIBUTION_ID", value = aws_cloudfront_distribution.images.id },
      { name = "MAIL_PORT", value = "587" },
      { name = "MAIL_SMTP_AUTH", value = "true" },
      { name = "MAIL_STARTTLS_ENABLED", value = "true" },
      { name = "MAIL_STARTTLS_REQUIRED", value = "true" },
    ]
    secrets = concat(
      [for secret_name in local.backend_application_secret_keys : {
        name      = secret_name
        valueFrom = "${data.aws_secretsmanager_secret.backend_application.arn}:${secret_name}::"
      }],
      [
        {
          name      = "SPRING_DATASOURCE_USERNAME"
          valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:username::"
        },
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:password::"
        },
        {
          name      = "FIREBASE_CREDENTIALS_JSON"
          valueFrom = data.aws_secretsmanager_secret.firebase_credentials.arn
        },
      ]
    )
    healthCheck = {
      command     = ["CMD-SHELL", "curl --fail --silent http://localhost:8080/livez >/dev/null"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 90
    }
    stopTimeout = 30
    logConfiguration = {
      logDriver = "awslogs"
      options   = local.backend_log_options
    }
  }])

  tags = {
    Name = "${local.name_prefix}-backend"
  }
}

resource "aws_ecs_service" "backend" {
  name            = "${local.name_prefix}-backend"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count

  deployment_maximum_percent         = 200
  deployment_minimum_healthy_percent = 100
  health_check_grace_period_seconds  = 120

  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.ec2.name
    base              = 1
    weight            = 100
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = local.backend_container_name
    container_port   = 8080
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [
    aws_ecs_cluster_capacity_providers.this,
    aws_iam_role_policy.backend_execution_secrets,
    aws_iam_role_policy.backend_images,
    aws_lb_listener.http,
  ]

  lifecycle {
    # GitHub Actions registers image-specific revisions and owns the active service revision.
    # Terraform continues to own the baseline task definition, roles, environment, and service configuration.
    ignore_changes = [task_definition]

    precondition {
      condition     = var.backend_desired_count == 0 || var.ecs_desired_capacity >= 1
      error_message = "Running the backend requires at least one ECS container instance."
    }
  }

  tags = {
    Name = "${local.name_prefix}-backend"
  }
}
