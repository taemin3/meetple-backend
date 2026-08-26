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
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.project_name))
    error_message = "project_name must start with a lowercase letter and contain only lowercase letters, numbers, and hyphens."
  }
}

variable "environment" {
  description = "Deployment environment name."
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "environment must be staging or production."
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

variable "ecs_instance_type" {
  description = "EC2 instance type used by the ECS capacity provider."
  type        = string
  default     = "t3.small"
}

variable "ecs_ami_ssm_parameter" {
  description = "SSM parameter containing the recommended ECS-optimized Amazon Linux 2023 AMI ID."
  type        = string
  default     = "/aws/service/ecs/optimized-ami/amazon-linux-2023/recommended/image_id"
}

variable "ecs_root_volume_size" {
  description = "Encrypted gp3 root volume size for each ECS container instance in GiB."
  type        = number
  default     = 30

  validation {
    condition     = var.ecs_root_volume_size >= 30
    error_message = "ecs_root_volume_size must be at least 30 GiB."
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
