variable "aws_region" {
  description = "AWS region for the Jenkins agent."
  type        = string
  default     = "ap-southeast-1"
}

variable "name_prefix" {
  description = "Name prefix used for Jenkins agent AWS resources."
  type        = string
  default     = "docvault-jenkins-agent"
}

variable "environment" {
  description = "Environment name used in tags."
  type        = string
  default     = "testing"
}

variable "create_network" {
  description = "Create a small public VPC/subnet for the Jenkins agent. Set false to use vpc_id and subnet_id."
  type        = bool
  default     = true
}

variable "vpc_id" {
  description = "Existing VPC ID to use when create_network is false."
  type        = string
  default     = null
}

variable "subnet_id" {
  description = "Existing subnet ID to use when create_network is false."
  type        = string
  default     = null
}

variable "vpc_cidr" {
  description = "CIDR for the Jenkins agent VPC when create_network is true."
  type        = string
  default     = "10.31.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR for the Jenkins agent public subnet when create_network is true."
  type        = string
  default     = "10.31.1.0/24"
}

variable "availability_zone" {
  description = "Optional availability zone for the public subnet. Defaults to the first available AZ."
  type        = string
  default     = null
}

variable "controller_cidr_blocks" {
  description = "CIDR blocks allowed to SSH to the agent from the local Jenkins controller."
  type        = list(string)
  default     = []
}

variable "admin_cidr_blocks" {
  description = "Additional CIDR blocks allowed to SSH to the agent for admin debugging."
  type        = list(string)
  default     = []
}

variable "ssh_key_name" {
  description = "Optional existing EC2 key pair name for admin SSH as the ubuntu user. If null, Terraform creates one and stores the private key in state."
  type        = string
  default     = null
}

variable "agent_instance_type" {
  description = "EC2 instance type for the Jenkins build agent. Docker image builds need more CPU/RAM than the controller."
  type        = string
  default     = "t3.large"
}

variable "agent_root_volume_size" {
  description = "Encrypted gp3 root volume size for the agent in GiB."
  type        = number
  default     = 80
}

variable "associate_public_ip_address" {
  description = "Associate a public IP to the agent. Keep true when the local controller reaches the agent over public SSH."
  type        = bool
  default     = true
}

variable "create_agent_eip" {
  description = "Create and attach an Elastic IP for the Jenkins agent so the local controller has a stable SSH target."
  type        = bool
  default     = true
}

variable "create_ssm_instance_profile" {
  description = "Create and attach an IAM instance profile that allows AWS Systems Manager Session Manager access for debugging."
  type        = bool
  default     = true
}

variable "agent_iam_instance_profile" {
  description = "Optional IAM instance profile name for the agent. Overrides the generated SSM profile."
  type        = string
  default     = null
}

variable "jenkins_agent_public_key" {
  description = "Optional public SSH key Jenkins will use to connect to the agent. If null, Terraform generates one and stores the private key in state."
  type        = string
  default     = null
}

variable "jenkins_agent_workspace" {
  description = "Remote root directory configured for the Jenkins permanent agent."
  type        = string
  default     = "/home/jenkins/agent"
}

variable "kubectl_version" {
  description = "kubectl version installed on the Jenkins agent."
  type        = string
  default     = "v1.35.0"
}

variable "extra_tags" {
  description = "Additional tags applied to all supported resources."
  type        = map(string)
  default     = {}
}
