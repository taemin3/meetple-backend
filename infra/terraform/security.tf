resource "aws_security_group" "alb" {
  name_prefix = "${local.name_prefix}-alb-"
  description = "Public ingress for the Meetple application load balancer"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${local.name_prefix}-alb"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  for_each = var.allowed_ingress_cidrs

  security_group_id = aws_security_group.alb.id
  description       = "HTTP ingress"
  cidr_ipv4         = each.value
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  for_each = var.certificate_arn == null ? toset([]) : var.allowed_ingress_cidrs

  security_group_id = aws_security_group.alb.id
  description       = "HTTPS ingress"
  cidr_ipv4         = each.value
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_ecs" {
  security_group_id            = aws_security_group.alb.id
  description                  = "Forward requests to dynamic ECS bridge-mode host ports"
  referenced_security_group_id = aws_security_group.ecs_instances.id
  from_port                    = 32768
  to_port                      = 65535
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "ecs_instances" {
  name_prefix = "${local.name_prefix}-ecs-instances-"
  description = "ECS container instances; no public inbound access"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${local.name_prefix}-ecs-instances"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_egress_rule" "ecs_instances_all" {
  security_group_id = aws_security_group.ecs_instances.id
  description       = "ECS agent, ECR, SSM, and package access"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_ingress_rule" "ecs_instances_from_alb" {
  security_group_id            = aws_security_group.ecs_instances.id
  description                  = "Dynamic bridge-mode host ports from the ALB"
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = 32768
  to_port                      = 65535
  ip_protocol                  = "tcp"
}

resource "aws_security_group" "rds" {
  name_prefix = "${local.name_prefix}-rds-"
  description = "PostgreSQL access from ECS application tasks only"
  vpc_id      = aws_vpc.this.id

  tags = {
    Name = "${local.name_prefix}-rds"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_app" {
  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL from bridge-mode ECS application tasks"
  referenced_security_group_id = aws_security_group.ecs_instances.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}
