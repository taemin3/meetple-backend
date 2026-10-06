locals {
  admin_origin_id     = "${local.name_prefix}-admin-s3"
  admin_api_origin_id = "${local.name_prefix}-admin-api"
}

data "aws_cloudfront_cache_policy" "admin_caching_disabled" {
  count = var.admin_hosting_enabled ? 1 : 0
  name  = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "admin_all_viewer_except_host" {
  count = var.admin_hosting_enabled ? 1 : 0
  name  = "Managed-AllViewerExceptHostHeader"
}

data "aws_cloudfront_response_headers_policy" "admin_security_headers" {
  count = var.admin_hosting_enabled ? 1 : 0
  name  = "Managed-SecurityHeadersPolicy"
}

resource "aws_s3_bucket" "admin" {
  count = var.admin_hosting_enabled ? 1 : 0

  bucket        = "${local.name_prefix}-admin-web-${data.aws_caller_identity.current.account_id}"
  force_destroy = var.admin_bucket_force_destroy
}

resource "aws_s3_bucket_public_access_block" "admin" {
  count = var.admin_hosting_enabled ? 1 : 0

  bucket                  = aws_s3_bucket.admin[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "admin" {
  count = var.admin_hosting_enabled ? 1 : 0

  bucket = aws_s3_bucket.admin[0].id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "admin" {
  count = var.admin_hosting_enabled ? 1 : 0

  bucket = aws_s3_bucket.admin[0].id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_cloudfront_origin_access_control" "admin" {
  count = var.admin_hosting_enabled ? 1 : 0

  name                              = "${local.name_prefix}-admin"
  description                       = "Private S3 access for the Meetple admin SPA"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_function" "admin_spa_rewrite" {
  count = var.admin_hosting_enabled ? 1 : 0

  name    = "${local.name_prefix}-admin-spa-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Rewrite extensionless admin routes to index.html"
  publish = true
  code    = <<-JAVASCRIPT
    function handler(event) {
      var request = event.request;
      if (!request.uri.startsWith('/api/') && !request.uri.includes('.')) {
        request.uri = '/index.html';
      }
      return request;
    }
  JAVASCRIPT
}

resource "aws_cloudfront_distribution" "admin" {
  count = var.admin_hosting_enabled ? 1 : 0

  enabled             = true
  comment             = "${local.name_prefix} admin SPA"
  default_root_object = "index.html"
  aliases             = sort(tolist(var.admin_domain_names))
  price_class         = "PriceClass_200"
  wait_for_deployment = false

  origin {
    domain_name              = aws_s3_bucket.admin[0].bucket_regional_domain_name
    origin_id                = local.admin_origin_id
    origin_access_control_id = aws_cloudfront_origin_access_control.admin[0].id
  }

  origin {
    domain_name = coalesce(var.admin_api_origin_domain_name, "invalid.local")
    origin_id   = local.admin_api_origin_id

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = local.admin_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    compress               = true

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.admin_spa_rewrite[0].arn
    }

    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.admin_security_headers[0].id
    min_ttl                    = 0
    default_ttl                = 300
    max_ttl                    = 3600
  }

  ordered_cache_behavior {
    path_pattern           = "/api/*"
    target_origin_id       = local.admin_api_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD", "OPTIONS"]
    compress               = true

    cache_policy_id            = data.aws_cloudfront_cache_policy.admin_caching_disabled[0].id
    origin_request_policy_id   = data.aws_cloudfront_origin_request_policy.admin_all_viewer_except_host[0].id
    response_headers_policy_id = data.aws_cloudfront_response_headers_policy.admin_security_headers[0].id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = length(var.admin_domain_names) == 0
    acm_certificate_arn            = length(var.admin_domain_names) > 0 ? var.admin_certificate_arn : null
    ssl_support_method             = length(var.admin_domain_names) > 0 ? "sni-only" : null
    minimum_protocol_version       = length(var.admin_domain_names) > 0 ? "TLSv1.2_2021" : "TLSv1"
  }

  lifecycle {
    precondition {
      condition     = var.admin_api_origin_domain_name != null
      error_message = "admin_api_origin_domain_name is required when admin_hosting_enabled is true."
    }

    precondition {
      condition     = length(var.admin_domain_names) == 0 || var.admin_certificate_arn != null
      error_message = "admin_certificate_arn is required when admin_domain_names is not empty."
    }
  }

  tags = {
    Name = "${local.name_prefix}-admin"
  }
}

data "aws_iam_policy_document" "admin_bucket" {
  count = var.admin_hosting_enabled ? 1 : 0

  statement {
    sid       = "AllowCloudFrontRead"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.admin[0].arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.admin[0].arn]
    }
  }
}

resource "aws_s3_bucket_policy" "admin" {
  count = var.admin_hosting_enabled ? 1 : 0

  bucket = aws_s3_bucket.admin[0].id
  policy = data.aws_iam_policy_document.admin_bucket[0].json

  depends_on = [aws_s3_bucket_public_access_block.admin]
}
