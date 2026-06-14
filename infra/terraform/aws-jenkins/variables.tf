variable "aws_region" {
  description = "AWS region for the Jenkins controller and agent."
  type        = string
  default     = "ap-southeast-1"
}

variable "name_prefix" {
  description = "Name prefix used for Jenkins AWS resources."
  type        = string
  default     = "docvault-jenkins"
}

variable "environment" {
  description = "Environment name used in tags."
  type        = string
  default     = "testing"
}

variable "create_network" {
  description = "Create a small public VPC/subnet for Jenkins. Set false to use vpc_id and subnet_id."
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
  description = "CIDR for the Jenkins VPC when create_network is true."
  type        = string
  default     = "10.30.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR for the Jenkins public subnet when create_network is true."
  type        = string
  default     = "10.30.1.0/24"
}

variable "availability_zone" {
  description = "Optional availability zone for the public subnet. Defaults to the first available AZ."
  type        = string
  default     = null
}

variable "admin_cidr_blocks" {
  description = "Backward-compatible default CIDR blocks for Jenkins UI and admin SSH. Prefer jenkins_ui_cidr_blocks and admin_ssh_cidr_blocks for new setups."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "jenkins_ui_cidr_blocks" {
  description = "CIDR blocks allowed to reach Jenkins UI on port 8080. If null, admin_cidr_blocks is used."
  type        = list(string)
  default     = null
}

variable "admin_ssh_cidr_blocks" {
  description = "CIDR blocks allowed to reach SSH on port 22. Set [] when using AWS Systems Manager Session Manager instead of public SSH."
  type        = list(string)
  default     = null
}

variable "ssh_key_name" {
  description = "Optional existing EC2 key pair name for admin SSH as the ubuntu user. If null, Terraform creates one and stores the private key in state."
  type        = string
  default     = null
}

variable "controller_instance_type" {
  description = "EC2 instance type for the Jenkins controller."
  type        = string
  default     = "t3.medium"
}

variable "agent_instance_type" {
  description = "EC2 instance type for the Jenkins build agent. Docker image builds need more CPU/RAM than the controller."
  type        = string
  default     = "t3.large"
}

variable "controller_root_volume_size" {
  description = "Encrypted gp3 root volume size for the controller in GiB."
  type        = number
  default     = 50
}

variable "agent_root_volume_size" {
  description = "Encrypted gp3 root volume size for the agent in GiB."
  type        = number
  default     = 80
}

variable "associate_public_ip_address" {
  description = "Associate public IPs to controller and agent. Keep true for quick labs in a public subnet."
  type        = bool
  default     = true
}

variable "create_controller_eip" {
  description = "Create and attach an Elastic IP for the Jenkins controller."
  type        = bool
  default     = true
}

variable "create_agent_eip" {
  description = "Create and attach an Elastic IP for the Jenkins agent. Useful when EKS API or NodePort access is CIDR-restricted."
  type        = bool
  default     = true
}

variable "create_ssm_instance_profile" {
  description = "Create and attach an IAM instance profile that allows AWS Systems Manager Session Manager access."
  type        = bool
  default     = true
}

variable "controller_iam_instance_profile" {
  description = "Optional IAM instance profile name for the controller."
  type        = string
  default     = null
}

variable "agent_iam_instance_profile" {
  description = "Optional IAM instance profile name for the agent."
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

variable "cloudflared_tunnel_token" {
  description = "Optional Cloudflare Tunnel connector token for publishing Jenkins UI without opening port 8080. Create the tunnel/public hostname in Cloudflare Zero Trust and paste the connector token here."
  type        = string
  default     = null
  sensitive   = true
}

variable "extra_tags" {
  description = "Additional tags applied to all supported resources."
  type        = map(string)
  default     = {}
}
