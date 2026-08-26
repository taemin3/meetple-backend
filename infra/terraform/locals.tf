locals {
  name_prefix        = "${var.project_name}-${var.environment}"
  availability_zones = slice(data.aws_availability_zones.available.names, 0, 2)

  public_subnets = {
    for index, availability_zone in local.availability_zones :
    availability_zone => cidrsubnet(var.vpc_cidr, 4, index)
  }

  database_subnets = {
    for index, availability_zone in local.availability_zones :
    availability_zone => cidrsubnet(var.vpc_cidr, 4, index + 8)
  }

  common_tags = merge(
    {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
    },
    var.additional_tags
  )
}
