terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # MVP uses local state. Move this to an S3 backend with DynamoDB locking
  # after the first successful demo apply.
  # backend "s3" {
  #   bucket         = "docvault-terraform-state-<account-id>"
  #   key            = "eks/terraform.tfstate"
  #   region         = "ap-southeast-1"
  #   dynamodb_table = "docvault-terraform-locks"
  #   encrypt        = true
  # }
}
