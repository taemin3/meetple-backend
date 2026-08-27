resource "aws_db_parameter_group" "postgres_cdc" {
  name_prefix = "${local.name_prefix}-postgres-cdc-"
  family      = "postgres${var.postgres_engine_version}"
  description = "Meetple PostgreSQL logical replication settings"

  parameter {
    name         = "rds.logical_replication"
    value        = "1"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "max_replication_slots"
    value        = tostring(var.rds_replication_slots)
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "max_wal_senders"
    value        = tostring(var.rds_replication_slots)
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "max_slot_wal_keep_size"
    value        = tostring(var.rds_max_slot_wal_keep_size_mb)
    apply_method = "pending-reboot"
  }

  tags = {
    Name = "${local.name_prefix}-postgres-cdc"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "postgres" {
  identifier = "${local.name_prefix}-postgres"

  engine         = "postgres"
  engine_version = var.postgres_engine_version
  instance_class = var.db_instance_class

  db_name  = var.db_name
  username = var.db_master_username
  port     = 5432

  manage_master_user_password = true

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  parameter_group_name   = aws_db_parameter_group.postgres_cdc.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = var.db_multi_az

  backup_retention_period = var.db_backup_retention_days
  backup_window           = "18:00-19:00"
  maintenance_window      = "sun:19:00-sun:20:00"

  auto_minor_version_upgrade      = true
  copy_tags_to_snapshot           = true
  deletion_protection             = var.db_deletion_protection
  skip_final_snapshot             = var.db_skip_final_snapshot
  final_snapshot_identifier       = var.db_skip_final_snapshot ? null : var.db_final_snapshot_identifier
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  performance_insights_enabled = false

  lifecycle {
    precondition {
      condition     = var.db_max_allocated_storage >= var.db_allocated_storage
      error_message = "db_max_allocated_storage must be greater than or equal to db_allocated_storage."
    }

    precondition {
      condition     = var.db_skip_final_snapshot || (var.db_final_snapshot_identifier != null && var.db_final_snapshot_identifier != "")
      error_message = "db_final_snapshot_identifier is required when db_skip_final_snapshot is false."
    }


    precondition {
      condition = (
        local.environment != "production" ||
        (var.db_multi_az && var.db_deletion_protection && !var.db_skip_final_snapshot)
      )
      error_message = "production requires Multi-AZ, deletion protection, and a final snapshot."
    }
  }

  tags = {
    Name = "${local.name_prefix}-postgres"
  }
}
