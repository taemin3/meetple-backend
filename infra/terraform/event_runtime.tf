locals {
  event_runtime_service_name = "event-runtime"
  event_runtime_dns_name     = "${local.event_runtime_service_name}.${aws_service_discovery_private_dns_namespace.this.name}"

  kafka_topics = [
    "meetple.push.notification.v1",
    "meetple.push.notification.v1.retry-0",
    "meetple.push.notification.v1.retry-1",
    "meetple.push.notification.v1.retry-2",
    "meetple.push.notification.v1.retry-3",
    "meetple.push.notification.v1.dlq",
    "meetple.push.chat.v1",
    "meetple.push.chat.v1.retry-0",
    "meetple.push.chat.v1.retry-1",
    "meetple.push.chat.v1.retry-2",
    "meetple.push.chat.v1.retry-3",
    "meetple.push.chat.v1.dlq",
    "meetple.image.delete.v1",
    "meetple.image.delete.v1.retry-0",
    "meetple.image.delete.v1.retry-1",
    "meetple.image.delete.v1.retry-2",
    "meetple.image.delete.v1.retry-3",
    "meetple.image.delete.v1.dlq",
    "meetple.email.delivery.v1",
    "meetple.email.delivery.v1.retry-0",
    "meetple.email.delivery.v1.retry-1",
    "meetple.email.delivery.v1.retry-2",
    "meetple.email.delivery.v1.dlq",
    "__debezium-heartbeat.meetple-outbox",
    "meetple-outbox.public.debezium_heartbeat",
  ]

  kafka_email_topics     = [for topic in local.kafka_topics : topic if startswith(topic, "meetple.email.delivery.v1")]
  kafka_heartbeat_topics = [for topic in local.kafka_topics : topic if startswith(topic, "__debezium-heartbeat.") || endswith(topic, ".debezium_heartbeat")]
  debezium_connector_config = jsondecode(
    file("${path.module}/../../docker/debezium/connectors/meetple-outbox-connector.json")
  ).config

  event_runtime_log_options = {
    awslogs-group         = aws_cloudwatch_log_group.event_runtime.name
    awslogs-region        = var.aws_region
    awslogs-stream-prefix = "ecs"
  }
}

data "aws_iam_policy_document" "event_runtime_execution_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "event_runtime_execution" {
  name               = "${local.name_prefix}-event-runtime-execution"
  assume_role_policy = data.aws_iam_policy_document.event_runtime_execution_assume_role.json
}

resource "aws_iam_role_policy_attachment" "event_runtime_execution" {
  role       = aws_iam_role.event_runtime_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "event_runtime_secret" {
  statement {
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_db_instance.postgres.master_user_secret[0].secret_arn]
  }
}

resource "aws_iam_role_policy" "event_runtime_secret" {
  name   = "rds-master-secret"
  role   = aws_iam_role.event_runtime_execution.id
  policy = data.aws_iam_policy_document.event_runtime_secret.json
}

resource "aws_cloudwatch_log_group" "event_runtime" {
  name              = "/ecs/${local.name_prefix}/event-runtime"
  retention_in_days = 14
}

resource "aws_service_discovery_private_dns_namespace" "this" {
  name        = "${local.name_prefix}.internal"
  description = "Private ECS service discovery for Meetple"
  vpc         = aws_vpc.this.id
}

resource "aws_service_discovery_service" "event_runtime" {
  name = local.event_runtime_service_name

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.this.id
    routing_policy = "MULTIVALUE"

    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_ecs_task_definition" "event_runtime" {
  family                   = "${local.name_prefix}-event-runtime"
  requires_compatibilities = ["EC2"]
  network_mode             = "awsvpc"
  execution_role_arn       = aws_iam_role.event_runtime_execution.arn

  volume {
    name = "kafka-data"

    docker_volume_configuration {
      scope         = "shared"
      autoprovision = true
      driver        = "local"

      labels = {
        Project     = var.project_name
        Environment = local.environment
        Component   = "kafka"
      }
    }
  }

  volume {
    name = "redis-data"

    docker_volume_configuration {
      scope         = "shared"
      autoprovision = true
      driver        = "local"

      labels = {
        Project     = var.project_name
        Environment = local.environment
        Component   = "redis"
      }
    }
  }

  container_definitions = jsonencode([
    {
      name              = "kafka"
      image             = var.kafka_image
      essential         = true
      cpu               = 512
      memoryReservation = 2048
      memory            = 3072
      portMappings = [{
        name          = "kafka"
        containerPort = 9092
        hostPort      = 9092
        protocol      = "tcp"
      }]
      environment = [
        { name = "CLUSTER_ID", value = var.kafka_cluster_id },
        { name = "KAFKA_NODE_ID", value = "1" },
        { name = "KAFKA_PROCESS_ROLES", value = "broker,controller" },
        { name = "KAFKA_LISTENERS", value = "INTERNAL://:19092,EXTERNAL://:9092,CONTROLLER://:9093" },
        { name = "KAFKA_ADVERTISED_LISTENERS", value = "INTERNAL://localhost:19092,EXTERNAL://${local.event_runtime_dns_name}:9092" },
        { name = "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP", value = "CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT" },
        { name = "KAFKA_INTER_BROKER_LISTENER_NAME", value = "INTERNAL" },
        { name = "KAFKA_CONTROLLER_LISTENER_NAMES", value = "CONTROLLER" },
        { name = "KAFKA_CONTROLLER_QUORUM_VOTERS", value = "1@localhost:9093" },
        { name = "KAFKA_LOG_DIRS", value = "/var/lib/kafka/data" },
        { name = "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", value = "1" },
        { name = "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", value = "1" },
        { name = "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", value = "1" },
        { name = "KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR", value = "1" },
        { name = "KAFKA_SHARE_COORDINATOR_STATE_TOPIC_MIN_ISR", value = "1" },
        { name = "KAFKA_DEFAULT_REPLICATION_FACTOR", value = "1" },
        { name = "KAFKA_MIN_INSYNC_REPLICAS", value = "1" },
        { name = "KAFKA_NUM_PARTITIONS", value = tostring(var.kafka_topic_partitions) },
        { name = "KAFKA_AUTO_CREATE_TOPICS_ENABLE", value = "false" },
        { name = "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", value = "0" },
      ]
      mountPoints = [{
        sourceVolume  = "kafka-data"
        containerPath = "/var/lib/kafka/data"
        readOnly      = false
      }]
      healthCheck = {
        command     = ["CMD-SHELL", "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --list >/dev/null 2>&1"]
        interval    = 15
        timeout     = 10
        retries     = 10
        startPeriod = 30
      }
      logConfiguration = {
        logDriver = "awslogs"
        options   = local.event_runtime_log_options
      }
    },
    {
      name              = "kafka-init"
      image             = var.kafka_image
      essential         = false
      cpu               = 64
      memoryReservation = 64
      memory            = 512
      entryPoint        = ["/bin/bash", "-ec"]
      command = [<<-SCRIPT
        existing_topics="$(/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --list)"
        for topic in $KAFKA_TOPICS; do
          if ! printf '%s\n' "$existing_topics" | grep -Fxq "$topic"; then
            /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --create --if-not-exists --topic "$topic" --partitions "$KAFKA_TOPIC_PARTITIONS" --replication-factor 1
          fi
        done
        for topic in $KAFKA_SHORT_RETENTION_TOPICS; do
          /opt/kafka/bin/kafka-configs.sh --bootstrap-server localhost:19092 --entity-type topics --entity-name "$topic" --alter --add-config retention.ms=86400000
        done
      SCRIPT
      ]
      environment = [
        { name = "KAFKA_TOPICS", value = join(" ", local.kafka_topics) },
        { name = "KAFKA_SHORT_RETENTION_TOPICS", value = join(" ", concat(local.kafka_email_topics, local.kafka_heartbeat_topics)) },
        { name = "KAFKA_TOPIC_PARTITIONS", value = tostring(var.kafka_topic_partitions) },
        { name = "KAFKA_HEAP_OPTS", value = "-Xms64m -Xmx256m" },
      ]
      dependsOn = [{
        containerName = "kafka"
        condition     = "HEALTHY"
      }]
      logConfiguration = {
        logDriver = "awslogs"
        options   = local.event_runtime_log_options
      }
    },
    {
      name              = "redis"
      image             = var.redis_image
      essential         = true
      cpu               = 128
      memoryReservation = 256
      memory            = 512
      command           = ["redis-server", "--appendonly", "yes"]
      portMappings = [{
        name          = "redis"
        containerPort = 6379
        hostPort      = 6379
        protocol      = "tcp"
      }]
      mountPoints = [{
        sourceVolume  = "redis-data"
        containerPath = "/data"
        readOnly      = false
      }]
      healthCheck = {
        command     = ["CMD", "redis-cli", "ping"]
        interval    = 10
        timeout     = 5
        retries     = 5
        startPeriod = 10
      }
      logConfiguration = {
        logDriver = "awslogs"
        options   = local.event_runtime_log_options
      }
    },
    {
      name              = "kafka-connect"
      image             = var.debezium_connect_image
      essential         = true
      cpu               = 256
      memoryReservation = 1024
      memory            = 1536
      portMappings = [{
        name          = "kafka-connect"
        containerPort = 8083
        hostPort      = 8083
        protocol      = "tcp"
      }]
      environment = [
        { name = "KAFKA_HEAP_OPTS", value = "-Xms256M -Xmx1024M" },
        { name = "BOOTSTRAP_SERVERS", value = "localhost:19092" },
        { name = "GROUP_ID", value = "meetple-debezium-connect" },
        { name = "CONFIG_STORAGE_TOPIC", value = "meetple.connect.configs" },
        { name = "OFFSET_STORAGE_TOPIC", value = "meetple.connect.offsets" },
        { name = "STATUS_STORAGE_TOPIC", value = "meetple.connect.statuses" },
        { name = "CONFIG_STORAGE_REPLICATION_FACTOR", value = "1" },
        { name = "OFFSET_STORAGE_REPLICATION_FACTOR", value = "1" },
        { name = "STATUS_STORAGE_REPLICATION_FACTOR", value = "1" },
        { name = "CONNECT_CONFIG_PROVIDERS", value = "env" },
        { name = "CONNECT_CONFIG_PROVIDERS_ENV_CLASS", value = "org.apache.kafka.common.config.provider.EnvVarConfigProvider" },
        { name = "CONNECT_KEY_CONVERTER", value = "org.apache.kafka.connect.storage.StringConverter" },
        { name = "CONNECT_VALUE_CONVERTER", value = "org.apache.kafka.connect.json.JsonConverter" },
        { name = "CONNECT_VALUE_CONVERTER_SCHEMAS_ENABLE", value = "false" },
        { name = "REST_HOST_NAME", value = "0.0.0.0" },
        { name = "POSTGRES_HOST", value = aws_db_instance.postgres.address },
        { name = "POSTGRES_DB", value = var.db_name },
      ]
      secrets = [
        { name = "POSTGRES_USER", valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:username::" },
        { name = "POSTGRES_PASSWORD", valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:password::" },
      ]
      dependsOn = [
        { containerName = "kafka", condition = "HEALTHY" },
        { containerName = "kafka-init", condition = "SUCCESS" },
      ]
      healthCheck = {
        command     = ["CMD-SHELL", "curl --fail --silent http://localhost:8083/connectors >/dev/null"]
        interval    = 15
        timeout     = 5
        retries     = 10
        startPeriod = 30
      }
      logConfiguration = {
        logDriver = "awslogs"
        options   = local.event_runtime_log_options
      }
    },
    {
      name              = "connector-manager"
      image             = var.debezium_connect_image
      essential         = true
      cpu               = 64
      memoryReservation = 64
      memory            = 128
      entryPoint        = ["/bin/bash", "-ec"]
      command = [<<-SCRIPT
        apply_connector_config() {
          curl --silent --show-error --request PUT \
            --header 'Content-Type: application/json' \
            --data "$CONNECTOR_CONFIG" \
            http://localhost:8083/connectors/meetple-outbox-connector/config || true
        }

        apply_connector_config

        consecutive_restarting_checks=0

        while true; do
          status="$(curl --silent --show-error http://localhost:8083/connectors/meetple-outbox-connector/status || true)"
          running_count="$(printf '%s' "$status" | grep -Eo '"state"[[:space:]]*:[[:space:]]*"RUNNING"' | wc -l | tr -d ' ')"
          failed_count="$(printf '%s' "$status" | grep -Eo '"state"[[:space:]]*:[[:space:]]*"FAILED"' | wc -l | tr -d ' ')"
          restarting_count="$(printf '%s' "$status" | grep -Eo '"state"[[:space:]]*:[[:space:]]*"RESTARTING"' | wc -l | tr -d ' ')"

          if [ "$restarting_count" -gt 0 ]; then
            consecutive_restarting_checks=$((consecutive_restarting_checks + 1))
          else
            consecutive_restarting_checks=0
          fi

          if printf '%s' "$status" | grep -Eq '"error_code"[[:space:]]*:[[:space:]]*404'; then
            echo "Debezium connector is missing; applying the connector configuration"
            apply_connector_config
          elif [ "$failed_count" -gt 0 ]; then
            echo "Debezium connector has failed tasks; requesting a failed-task restart"
            curl --silent --show-error --request POST \
              'http://localhost:8083/connectors/meetple-outbox-connector/restart?includeTasks=true&onlyFailed=true' || true
          elif [ "$running_count" -lt 2 ]; then
            echo "Debezium connector is still starting"
          fi

          if [ "$consecutive_restarting_checks" -ge 10 ]; then
            echo "Debezium connector task is stuck restarting"
          fi

          sleep 30
        done
      SCRIPT
      ]
      environment = [{
        name  = "CONNECTOR_CONFIG"
        value = jsonencode(local.debezium_connector_config)
      }]
      dependsOn = [{
        containerName = "kafka-connect"
        condition     = "HEALTHY"
      }]
      logConfiguration = {
        logDriver = "awslogs"
        options   = local.event_runtime_log_options
      }
    },
  ])

  tags = {
    Name = "${local.name_prefix}-event-runtime"
  }
}

resource "aws_ecs_service" "event_runtime" {
  name            = "${local.name_prefix}-event-runtime"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.event_runtime.arn
  desired_count   = 1

  deployment_maximum_percent         = 100
  deployment_minimum_healthy_percent = 0

  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.ec2.name
    base              = 1
    weight            = 100
  }

  network_configuration {
    subnets         = [for subnet in aws_subnet.public : subnet.id]
    security_groups = [aws_security_group.event_runtime.id]
  }

  service_registries {
    registry_arn = aws_service_discovery_service.event_runtime.arn
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [
    aws_ecs_cluster_capacity_providers.this,
    aws_iam_role_policy.event_runtime_secret,
  ]

  lifecycle {
    precondition {
      condition     = var.ecs_desired_capacity >= 1
      error_message = "The event runtime requires at least one ECS container instance."
    }
  }
}
