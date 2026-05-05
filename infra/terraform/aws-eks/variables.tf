variable "aws_region" {
  description = "AWS region for EKS."
  type        = string
  default     = "ap-southeast-1"
}

variable "cluster_name" {
  description = "EKS cluster name."
  type        = string
  default     = "docvault-eks"
}

variable "cluster_version" {
  description = "Kubernetes version for EKS."
  type        = string
  default     = "1.35"
}

variable "environment" {
  description = "Environment name used in tags."
  type        = string
  default     = "testing"
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "CIDR blocks allowed to reach the public EKS API endpoint. Replace 0.0.0.0/0 with your workstation public IP CIDR for a stricter demo."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_instance_types" {
  description = "EC2 instance types for the EKS managed node group."
  type        = list(string)
  default     = ["t3.large"]
}

variable "node_desired_size" {
  description = "Desired node count."
  type        = number
  default     = 2
}

variable "node_min_size" {
  description = "Minimum node count."
  type        = number
  default     = 1
}

variable "node_max_size" {
  description = "Maximum node count."
  type        = number
  default     = 3
}

variable "node_disk_size" {
  description = "Encrypted root volume size for each node in GiB."
  type        = number
  default     = 30
}

variable "enable_nat_gateway" {
  description = "Enable NAT Gateway and place nodes in private subnets. False keeps nodes in public subnets for lower-cost MVP demos."
  type        = bool
  default     = false
}
