variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "Short project name used in resource names and tags."
  type        = string
  default     = "meetple"

  validation {
    condition = (
      length(var.project_name) >= 2 &&
      length(var.project_name) <= 17 &&
      can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", var.project_name)) &&
      !strcontains(var.project_name, "--") &&
      var.project_name != "internal" &&
      !startswith(var.project_name, "internal-")
    )
    error_message = "project_name must be 2-17 lowercase letters, numbers, or single hyphens; it cannot end with a hyphen or start with internal-."
  }
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC. Two public and two database subnets are derived from it."
  type        = string
  default     = "10.20.0.0/16"
}

variable "allowed_ingress_cidrs" {
  description = "IPv4 CIDR blocks allowed to reach the public load balancer."
  type        = set(string)
  default     = ["0.0.0.0/0"]

  validation {
    condition     = length(var.allowed_ingress_cidrs) > 0 && alltrue([for cidr in var.allowed_ingress_cidrs : can(cidrhost(cidr, 0))])
    error_message = "allowed_ingress_cidrs must contain at least one valid IPv4 CIDR block."
  }
}

variable "certificate_arn" {
  description = "Optional ACM certificate ARN. When set, HTTP redirects to HTTPS and an HTTPS listener is created."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.certificate_arn == null || can(regex("^arn:aws[a-z-]*:acm:", var.certificate_arn))
    error_message = "certificate_arn must be null or an ACM certificate ARN."
  }
}

variable "enable_alb_deletion_protection" {
  description = "Prevent accidental ALB deletion. Enable this for production."
  type        = bool
  default     = false
}

variable "alb_idle_timeout_seconds" {
  description = "ALB connection idle timeout. A longer timeout keeps WebSocket chat connections alive."
  type        = number
  default     = 3600

  validation {
    condition = (
      var.alb_idle_timeout_seconds >= 60 &&
      var.alb_idle_timeout_seconds <= 4000 &&
      floor(var.alb_idle_timeout_seconds) == var.alb_idle_timeout_seconds
    )
    error_message = "alb_idle_timeout_seconds must be an integer between 60 and 4000."
  }
}

variable "ecs_instance_type" {
  description = "EC2 instance type used by the ECS capacity provider."
  type        = string
  default     = "t3.large"
}

variable "ecs_ami_ssm_parameter" {
  description = "SSM parameter containing the recommended ECS-optimized Amazon Linux 2023 AMI ID."
  type        = string
  default     = "/aws/service/ecs/optimized-ami/amazon-linux-2023/recommended/image_id"
}

variable "ecs_root_volume_size" {
  description = "Encrypted gp3 root volume size for each ECS container instance in GiB."
  type        = number
  default     = 50

  validation {
    condition     = var.ecs_root_volume_size >= 30
    error_message = "ecs_root_volume_size must be at least 30 GiB."
  }
}

variable "kafka_image" {
  description = "Kafka container image used by the single-node event runtime."
  type        = string
  default     = "apache/kafka:4.3.1"
}

variable "debezium_connect_image" {
  description = "Debezium Kafka Connect container image."
  type        = string
  default     = "quay.io/debezium/connect:3.6.0.Final"
}

variable "redis_image" {
  description = "Redis container image used by the event runtime."
  type        = string
  default     = "redis:7-alpine"
}

variable "backend_image_tag" {
  description = "Baseline ECR image tag for the Terraform task definition. GitHub Actions activates an immutable Git SHA revision before tasks start."
  type        = string
  default     = "bootstrap"

  validation {
    condition = (
      var.backend_image_tag != "latest" &&
      can(regex("^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$", var.backend_image_tag))
    )
    error_message = "backend_image_tag must be a valid immutable Docker tag and cannot be latest."
  }
}

variable "backend_desired_count" {
  description = "Desired Spring Boot task count. Keep 0 until the staging workflow activates a real image revision, then set 1."
  type        = number
  default     = 0

  validation {
    condition = (
      var.backend_desired_count >= 0 &&
      var.backend_desired_count <= 2 &&
      floor(var.backend_desired_count) == var.backend_desired_count
    )
    error_message = "backend_desired_count must be an integer between 0 and 2."
  }
}

variable "backend_application_secret_arn" {
  description = "Existing Secrets Manager ARN containing the Spring Boot application secret JSON keys documented in README.md."
  type        = string

  validation {
    condition     = can(regex("^arn:aws[a-z-]*:secretsmanager:", var.backend_application_secret_arn))
    error_message = "backend_application_secret_arn must be a Secrets Manager ARN."
  }
}

variable "firebase_credentials_secret_arn" {
  description = "Existing Secrets Manager ARN whose value is the complete Firebase service-account JSON document."
  type        = string

  validation {
    condition     = can(regex("^arn:aws[a-z-]*:secretsmanager:", var.firebase_credentials_secret_arn))
    error_message = "firebase_credentials_secret_arn must be a Secrets Manager ARN."
  }
}

variable "backend_secret_kms_key_arns" {
  description = "Customer-managed KMS key ARNs used by the application or Firebase secrets. Leave empty for the AWS managed Secrets Manager key."
  type        = set(string)
  default     = []

  validation {
    condition = alltrue([
      for arn in var.backend_secret_kms_key_arns : can(regex("^arn:aws[a-z-]*:kms:[^:]+:[0-9]{12}:key/", arn))
    ])
    error_message = "backend_secret_kms_key_arns must contain only customer-managed KMS key ARNs."
  }
}

variable "kafka_consumer_concurrency" {
  description = "Kafka listener concurrency for each Spring Boot consumer container. One limits staging memory use."
  type        = number
  default     = 1

  validation {
    condition = (
      var.kafka_consumer_concurrency >= 1 &&
      var.kafka_consumer_concurrency <= 3 &&
      floor(var.kafka_consumer_concurrency) == var.kafka_consumer_concurrency
    )
    error_message = "kafka_consumer_concurrency must be an integer between 1 and 3."
  }
}

variable "image_bucket_force_destroy" {
  description = "Allow Terraform to delete non-empty image buckets. Keep false unless disposable staging data is intended."
  type        = bool
  default     = false
}

variable "image_upload_allowed_origins" {
  description = "Optional browser origins allowed to upload directly with S3 presigned URLs. Native mobile apps do not require CORS."
  type        = set(string)
  default     = []
}

variable "kafka_cluster_id" {
  description = "Stable KRaft cluster ID for the single Kafka broker."
  type        = string
  default     = "4L6g3nShT-eMCtK--X86sw"

  validation {
    condition     = can(regex("^[A-Za-z0-9_-]{22}$", var.kafka_cluster_id))
    error_message = "kafka_cluster_id must be a 22-character base64url KRaft cluster ID."
  }
}

variable "kafka_topic_partitions" {
  description = "Partition count for Meetple application, retry, and DLQ topics."
  type        = number
  default     = 3

  validation {
    condition     = var.kafka_topic_partitions >= 1 && floor(var.kafka_topic_partitions) == var.kafka_topic_partitions
    error_message = "kafka_topic_partitions must be a positive integer."
  }
}

variable "rds_replication_slots" {
  description = "Maximum PostgreSQL replication slots and WAL senders available to CDC."
  type        = number
  default     = 10

  validation {
    condition     = var.rds_replication_slots >= 1 && floor(var.rds_replication_slots) == var.rds_replication_slots
    error_message = "rds_replication_slots must be a positive integer."
  }
}

variable "rds_max_slot_wal_keep_size_mb" {
  description = "Maximum WAL retained by each replication slot in MiB before PostgreSQL invalidates it."
  type        = number
  default     = 2048

  validation {
    condition = (
      var.rds_max_slot_wal_keep_size_mb >= 1024 &&
      floor(var.rds_max_slot_wal_keep_size_mb) == var.rds_max_slot_wal_keep_size_mb
    )
    error_message = "rds_max_slot_wal_keep_size_mb must be an integer of at least 1024 MiB."
  }
}

variable "rds_replication_slot_lag_alarm_threshold_mb" {
  description = "CloudWatch warning threshold for the oldest PostgreSQL replication slot lag in MiB. Increase only after the matching max_slot_wal_keep_size change is active following an RDS reboot."
  type        = number
  default     = 1536

  validation {
    condition = (
      var.rds_replication_slot_lag_alarm_threshold_mb >= 1 &&
      floor(var.rds_replication_slot_lag_alarm_threshold_mb) == var.rds_replication_slot_lag_alarm_threshold_mb &&
      var.rds_replication_slot_lag_alarm_threshold_mb < var.rds_max_slot_wal_keep_size_mb
    )
    error_message = "rds_replication_slot_lag_alarm_threshold_mb must be a positive integer below rds_max_slot_wal_keep_size_mb."
  }
}

variable "ecs_min_size" {
  description = "Minimum number of ECS container instances."
  type        = number
  default     = 1
}

variable "ecs_desired_capacity" {
  description = "Initial desired number of ECS container instances. ECS managed scaling owns later changes."
  type        = number
  default     = 1
}

variable "ecs_max_size" {
  description = "Maximum number of ECS container instances."
  type        = number
  default     = 2
}

variable "db_instance_class" {
  description = "RDS PostgreSQL instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "postgres_engine_version" {
  description = "PostgreSQL major version. AWS selects a supported minor version."
  type        = string
  default     = "16"

  validation {
    condition     = can(regex("^[0-9]+$", var.postgres_engine_version))
    error_message = "postgres_engine_version must be a PostgreSQL major version such as 16."
  }
}

variable "db_name" {
  description = "Initial PostgreSQL database name."
  type        = string
  default     = "meetple"
}

variable "db_master_username" {
  description = "RDS master username. Its password is generated and managed by RDS in Secrets Manager."
  type        = string
  default     = "meetple_admin"
}

variable "db_allocated_storage" {
  description = "Initial gp3 database storage in GiB."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Maximum autoscaled database storage in GiB."
  type        = number
  default     = 100
}

variable "db_backup_retention_days" {
  description = "Number of days to retain automated RDS backups."
  type        = number
  default     = 7

  validation {
    condition     = var.db_backup_retention_days >= 1 && var.db_backup_retention_days <= 35
    error_message = "db_backup_retention_days must be between 1 and 35."
  }
}

variable "db_multi_az" {
  description = "Create an RDS standby in another availability zone. Disabled by default to control staging cost."
  type        = bool
  default     = false
}

variable "db_deletion_protection" {
  description = "Prevent accidental RDS deletion. Enable this for production."
  type        = bool
  default     = false
}

variable "db_skip_final_snapshot" {
  description = "Skip the final RDS snapshot when destroying. Set false for production."
  type        = bool
  default     = true
}

variable "db_final_snapshot_identifier" {
  description = "Unique final snapshot identifier. Required when db_skip_final_snapshot is false."
  type        = string
  default     = null
  nullable    = true
}

variable "additional_tags" {
  description = "Additional tags applied to supported AWS resources."
  type        = map(string)
  default     = {}
}

variable "github_actions_deploy_enabled" {
  description = "Create the GitHub OIDC provider and least-privilege role used to deploy the staging backend."
  type        = bool
  default     = false
}

variable "github_actions_repository" {
  description = "GitHub repository allowed to assume the deployment role, in owner/repository format."
  type        = string
  default     = "taemin3/meetple-backend"

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_actions_repository))
    error_message = "github_actions_repository must use owner/repository format."
  }
}

variable "github_actions_oidc_provider_arn" {
  description = "Existing account-wide GitHub OIDC provider ARN. Leave null to let this workspace create it once per AWS account."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.github_actions_oidc_provider_arn == null ||
      can(regex("^arn:aws[a-z-]*:iam::[0-9]{12}:oidc-provider/token\\.actions\\.githubusercontent\\.com$", var.github_actions_oidc_provider_arn))
    )
    error_message = "github_actions_oidc_provider_arn must be null or the GitHub Actions OIDC provider ARN."
  }
}

variable "monitoring_notification_email" {
  description = "Optional email address subscribed to staging monitoring alerts. AWS sends a confirmation email after apply."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition = (
      var.monitoring_notification_email == null ||
      can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.monitoring_notification_email))
    )
    error_message = "monitoring_notification_email must be null or a valid email address."
  }
}

variable "monitoring_alarm_actions_enabled" {
  description = "Send SNS ALARM and OK notifications. Set false before intentionally stopping staging resources."
  type        = bool
  default     = true
}
