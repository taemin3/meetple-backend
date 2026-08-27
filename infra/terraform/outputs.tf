output "environment" {
  description = "Environment selected by the active Terraform workspace."
  value       = local.environment
}

output "vpc_id" {
  description = "VPC ID."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs for the ALB and ECS container instances."
  value       = [for subnet in aws_subnet.public : subnet.id]
}

output "database_subnet_ids" {
  description = "Private database subnet IDs."
  value       = [for subnet in aws_subnet.database : subnet.id]
}

output "alb_dns_name" {
  description = "Public ALB DNS name. It returns 503 until an ECS service registers healthy targets."
  value       = aws_lb.app.dns_name
}

output "alb_target_group_arn" {
  description = "Target group ARN to attach to the future ECS backend service."
  value       = aws_lb_target_group.app.arn
}

output "ecs_instance_security_group_id" {
  description = "Security group ID used by bridge-mode ECS container instances."
  value       = aws_security_group.ecs_instances.id
}

output "ecs_cluster_name" {
  description = "ECS cluster name."
  value       = aws_ecs_cluster.this.name
}

output "ecs_capacity_provider_name" {
  description = "ECS EC2 capacity provider name."
  value       = aws_ecs_capacity_provider.ec2.name
}

output "backend_log_group_name" {
  description = "CloudWatch log group reserved for the future backend ECS task."
  value       = aws_cloudwatch_log_group.backend.name
}

output "ecr_repository_url" {
  description = "ECR repository URL for immutable backend images."
  value       = aws_ecr_repository.backend.repository_url
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint including port."
  value       = aws_db_instance.postgres.endpoint
}

output "rds_master_secret_arn" {
  description = "Secrets Manager ARN containing the RDS-generated master credentials."
  value       = aws_db_instance.postgres.master_user_secret[0].secret_arn
  sensitive   = true
}

output "event_runtime_service_name" {
  description = "ECS service running Redis, Kafka, Kafka Connect, and the Debezium connector bootstrap."
  value       = aws_ecs_service.event_runtime.name
}

output "event_runtime_dns_name" {
  description = "Private Cloud Map hostname shared by the event runtime containers."
  value       = local.event_runtime_dns_name
}

output "kafka_bootstrap_servers" {
  description = "Private Kafka bootstrap address for future ECS application tasks."
  value       = "${local.event_runtime_dns_name}:9092"
}

output "redis_host" {
  description = "Private Redis hostname for future ECS application tasks."
  value       = local.event_runtime_dns_name
}

output "event_runtime_log_group_name" {
  description = "CloudWatch log group for Redis, Kafka, Kafka Connect, and bootstrap containers."
  value       = aws_cloudwatch_log_group.event_runtime.name
}

output "event_runtime_redeploy_automation_name" {
  description = "Systems Manager Automation runbook that forces a fresh ECS task after secret rotation."
  value       = aws_ssm_document.event_runtime_redeploy.name
}

output "rds_secret_rotation_event_rule_name" {
  description = "EventBridge rule that starts the event runtime redeployment workflow."
  value       = aws_cloudwatch_event_rule.rds_master_secret_rotated.name
}
