locals {
  github_oidc_provider_arn = var.github_actions_oidc_provider_arn != null ? var.github_actions_oidc_provider_arn : try(
    aws_iam_openid_connect_provider.github[0].arn,
    null,
  )
}

resource "aws_iam_openid_connect_provider" "github" {
  count = var.github_actions_deploy_enabled && var.github_actions_oidc_provider_arn == null ? 1 : 0

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
      values   = ["repo:${var.github_actions_repository}:environment:${var.github_actions_environment_name}"]
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
    resources = [aws_ecs_service.backend.arn]
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
