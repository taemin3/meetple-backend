locals {
  github_actions_admin_role_enabled = var.github_actions_admin_deploy_enabled && var.admin_hosting_enabled
  github_actions_any_deploy_enabled = (
    var.github_actions_deploy_enabled ||
    var.github_actions_ai_deploy_enabled ||
    local.github_actions_admin_role_enabled
  )
  github_oidc_provider_arn = var.github_actions_oidc_provider_arn != null ? var.github_actions_oidc_provider_arn : try(
    aws_iam_openid_connect_provider.github[0].arn,
    null,
  )
  github_actions_backend_service_arn = "arn:${data.aws_partition.current.partition}:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/${local.name_prefix}-cluster/${local.name_prefix}-backend"
  github_actions_ai_service_arn      = "arn:${data.aws_partition.current.partition}:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/${local.name_prefix}-cluster/${local.name_prefix}-ai"
}

resource "aws_iam_openid_connect_provider" "github" {
  count = local.github_actions_any_deploy_enabled && var.github_actions_oidc_provider_arn == null ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_policy_document" "github_actions_assume_role" {
  count = var.github_actions_deploy_enabled ? 1 : 0

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_actions_repository}:environment:staging"]
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  count = var.github_actions_deploy_enabled ? 1 : 0

  name               = "${local.name_prefix}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role[0].json

  tags = {
    Name = "${local.name_prefix}-github-deploy"
  }
}

data "aws_iam_policy_document" "github_actions_deploy" {
  count = var.github_actions_deploy_enabled ? 1 : 0

  statement {
    sid       = "GetEcrAuthorizationToken"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "PushBackendImage"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.backend.arn]
  }

  statement {
    sid    = "RegisterBackendTaskDefinition"
    effect = "Allow"
    actions = [
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "DeployBackendService"
    effect = "Allow"
    actions = [
      "ecs:DescribeServices",
      "ecs:UpdateService",
    ]
    resources = [local.github_actions_backend_service_arn]
  }

  statement {
    sid     = "PassBackendTaskRoles"
    effect  = "Allow"
    actions = ["iam:PassRole"]
    resources = [
      aws_iam_role.backend_execution.arn,
      aws_iam_role.backend_task.arn,
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  count = var.github_actions_deploy_enabled ? 1 : 0

  name   = "backend-deployment"
  role   = aws_iam_role.github_actions_deploy[0].id
  policy = data.aws_iam_policy_document.github_actions_deploy[0].json
}

data "aws_iam_policy_document" "github_actions_ai_assume_role" {
  count = var.github_actions_ai_deploy_enabled ? 1 : 0

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_actions_ai_repository}:environment:staging"]
    }
  }
}

resource "aws_iam_role" "github_actions_ai_deploy" {
  count = var.github_actions_ai_deploy_enabled ? 1 : 0

  name               = "${local.name_prefix}-github-ai-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_ai_assume_role[0].json

  tags = {
    Name = "${local.name_prefix}-github-ai-deploy"
  }
}

data "aws_iam_policy_document" "github_actions_ai_deploy" {
  count = var.github_actions_ai_deploy_enabled ? 1 : 0

  statement {
    sid       = "GetEcrAuthorizationToken"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "PushAiImage"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.ai.arn]
  }

  statement {
    sid    = "RegisterAiTaskDefinition"
    effect = "Allow"
    actions = [
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "DeployAiService"
    effect = "Allow"
    actions = [
      "ecs:DescribeServices",
      "ecs:UpdateService",
    ]
    resources = [local.github_actions_ai_service_arn]
  }

  statement {
    sid     = "PassAiTaskRoles"
    effect  = "Allow"
    actions = ["iam:PassRole"]
    resources = [
      aws_iam_role.ai_execution.arn,
      aws_iam_role.ai_task.arn,
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_actions_ai_deploy" {
  count = var.github_actions_ai_deploy_enabled ? 1 : 0

  name   = "ai-deployment"
  role   = aws_iam_role.github_actions_ai_deploy[0].id
  policy = data.aws_iam_policy_document.github_actions_ai_deploy[0].json
}

data "aws_iam_policy_document" "github_actions_admin_assume_role" {
  count = local.github_actions_admin_role_enabled ? 1 : 0

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_actions_admin_repository}:environment:staging"]
    }
  }
}

resource "aws_iam_role" "github_actions_admin_deploy" {
  count = local.github_actions_admin_role_enabled ? 1 : 0

  name               = "${local.name_prefix}-github-admin-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_admin_assume_role[0].json

  tags = {
    Name = "${local.name_prefix}-github-admin-deploy"
  }
}

data "aws_iam_policy_document" "github_actions_admin_deploy" {
  count = local.github_actions_admin_role_enabled ? 1 : 0

  statement {
    sid    = "ReadAdminBucket"
    effect = "Allow"
    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket",
    ]
    resources = [aws_s3_bucket.admin[0].arn]
  }

  statement {
    sid    = "DeployAdminObjects"
    effect = "Allow"
    actions = [
      "s3:DeleteObject",
      "s3:GetObject",
      "s3:PutObject",
    ]
    resources = ["${aws_s3_bucket.admin[0].arn}/*"]
  }

  statement {
    sid    = "InvalidateAdminDistribution"
    effect = "Allow"
    actions = [
      "cloudfront:CreateInvalidation",
      "cloudfront:GetDistribution",
    ]
    resources = [aws_cloudfront_distribution.admin[0].arn]
  }
}

resource "aws_iam_role_policy" "github_actions_admin_deploy" {
  count = local.github_actions_admin_role_enabled ? 1 : 0

  name   = "admin-deployment"
  role   = aws_iam_role.github_actions_admin_deploy[0].id
  policy = data.aws_iam_policy_document.github_actions_admin_deploy[0].json
}
