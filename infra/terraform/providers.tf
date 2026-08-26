provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ssm_parameter" "ecs_optimized_ami" {
  name = var.ecs_ami_ssm_parameter
}
