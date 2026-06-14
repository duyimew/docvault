output "agent_public_ip" {
  description = "Public IP address your local Jenkins controller should use as the SSH host."
  value       = local.agent_public_ip
}

output "agent_public_cidr" {
  description = "Agent public IP as a /32 CIDR."
  value       = "${local.agent_public_ip}/32"
}

output "agent_private_ip" {
  description = "Private IP address for the Jenkins agent."
  value       = aws_instance.agent.private_ip
}

output "agent_instance_id" {
  description = "EC2 instance ID for the Jenkins agent."
  value       = aws_instance.agent.id
}

output "agent_label" {
  description = "Label required by the repository Jenkinsfile."
  value       = "docker-agent-alpine-ubuntu-vm"
}

output "agent_remote_root_directory" {
  description = "Remote root directory to configure for the Jenkins permanent agent."
  value       = var.jenkins_agent_workspace
}

output "jenkins_agent_ssh_username" {
  description = "SSH username to configure in Jenkins for the permanent agent."
  value       = "jenkins"
}

output "jenkins_agent_ssh_private_key_pem" {
  description = "Private SSH key to add in Jenkins credentials when Terraform generated the key. Null if jenkins_agent_public_key was supplied."
  value       = var.jenkins_agent_public_key == null ? tls_private_key.jenkins_agent[0].private_key_pem : null
  sensitive   = true
}

output "jenkins_agent_ssh_public_key" {
  description = "Public SSH key installed in /home/jenkins/.ssh/authorized_keys on the agent."
  value       = local.jenkins_agent_public_key
}

output "admin_ssh_key_name" {
  description = "EC2 key pair name attached to the agent for ubuntu admin SSH."
  value       = local.admin_key_name
}

output "admin_ssh_private_key_pem" {
  description = "Private key for ubuntu admin SSH when Terraform generated the EC2 key pair. Null if ssh_key_name was supplied."
  value       = var.ssh_key_name == null ? tls_private_key.admin[0].private_key_pem : null
  sensitive   = true
}

output "admin_ssh_command" {
  description = "SSH command for ubuntu admin access when SSH ingress is allowed."
  value       = var.ssh_key_name == null ? "ssh -i docvault-jenkins-agent-admin.pem ubuntu@${local.agent_public_ip}" : "ssh ubuntu@${local.agent_public_ip}"
}

output "agent_ssm_session_command" {
  description = "Command to open an AWS Systems Manager shell session to the agent."
  value       = "aws ssm start-session --region ${var.aws_region} --target ${aws_instance.agent.id}"
}

output "vpc_id" {
  description = "VPC used by the Jenkins agent."
  value       = local.vpc_id
}

output "subnet_id" {
  description = "Subnet used by the Jenkins agent."
  value       = local.subnet_id
}
