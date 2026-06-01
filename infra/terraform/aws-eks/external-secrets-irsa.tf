data "aws_caller_identity" "current" {}

locals {
  external_secrets_namespace       = "external-secrets"
  external_secrets_service_account = "external-secrets"
  external_secrets_secret_prefix   = "/docvault/${var.environment}"
}

data "aws_iam_policy_document" "external_secrets_assume_role" {
  statement {
    effect = "Allow"

    actions = [
      "sts:AssumeRoleWithWebIdentity",
    ]

    principals {
      type        = "Federated"
      identifiers = [module.eks.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${module.eks.oidc_provider}:sub"
      values = [
        "system:serviceaccount:${local.external_secrets_namespace}:${local.external_secrets_service_account}",
      ]
    }
  }
}

resource "aws_iam_role" "external_secrets" {
  name               = "${local.name}-external-secrets"
  assume_role_policy = data.aws_iam_policy_document.external_secrets_assume_role.json
  tags               = local.tags
}

data "aws_iam_policy_document" "external_secrets" {
  statement {
    sid    = "ReadDocVaultSecrets"
    effect = "Allow"

    actions = [
      "secretsmanager:DescribeSecret",
      "secretsmanager:GetSecretValue",
      "secretsmanager:ListSecretVersionIds",
    ]

    resources = [
      "arn:aws:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${local.external_secrets_secret_prefix}/*",
    ]
  }
}

resource "aws_iam_policy" "external_secrets" {
  name   = "${local.name}-external-secrets"
  policy = data.aws_iam_policy_document.external_secrets.json
  tags   = local.tags
}

resource "aws_iam_role_policy_attachment" "external_secrets" {
  role       = aws_iam_role.external_secrets.name
  policy_arn = aws_iam_policy.external_secrets.arn
}
