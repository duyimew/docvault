terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Local state is enough for a quick lab/demo setup. Move this to S3 with
  # DynamoDB locking before sharing the environment with multiple operators.
  # backend "s3" {
  #   bucket         = "docvault-terraform-state-<account-id>"
  #   key            = "jenkins-agent/terraform.tfstate"
  #   region         = "ap-southeast-1"
  #   dynamodb_table = "docvault-terraform-locks"
  #   encrypt        = true
  # }
}
