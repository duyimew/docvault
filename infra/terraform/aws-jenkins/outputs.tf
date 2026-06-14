output "controller_public_ip" {
  description = "Public IP address for the Jenkins controller."
  value       = local.controller_public_ip
}

output "controller_public_cidr" {
  description = "Controller public IP as a /32 CIDR."
  value       = "${local.controller_public_ip}/32"
}

output "controller_private_ip" {
  description = "Private IP address for the Jenkins controller."
  value       = aws_instance.controller.private_ip
}

output "controller_instance_id" {
  description = "EC2 instance ID for the Jenkins controller."
  value       = aws_instance.controller.id
}

output "controller_url" {
  description = "Jenkins controller URL."
  value       = "http://${local.controller_public_ip}:8080"
}

output "controller_initial_password_command" {
  description = "Command to print the initial Jenkins admin password from the controller."
  value       = var.ssh_key_name == null ? "ssh -i docvault-jenkins-admin.pem ubuntu@${local.controller_public_ip} 'sudo cat /var/lib/jenkins/secrets/initialAdminPassword'" : "ssh ubuntu@${local.controller_public_ip} 'sudo cat /var/lib/jenkins/secrets/initialAdminPassword'"
}

output "controller_ssm_session_command" {
  description = "Command to open an AWS Systems Manager shell session to the controller."
  value       = "aws ssm start-session --region ${var.aws_region} --target ${aws_instance.controller.id}"
}

output "controller_ssm_port_forward_command" {
  description = "Command to access Jenkins UI through SSM port forwarding at http://127.0.0.1:8080."
  value       = "aws ssm start-session --region ${var.aws_region} --target ${aws_instance.controller.id} --document-name AWS-StartPortForwardingSession --parameters '{\"portNumber\":[\"8080\"],\"localPortNumber\":[\"8080\"]}'"
}

output "agent_private_ip" {
  description = "Private IP Jenkins should use as the SSH agent host."
  value       = aws_instance.agent.private_ip
}

output "agent_instance_id" {
  description = "EC2 instance ID for the Jenkins agent."
  value       = aws_instance.agent.id
}

output "agent_public_ip" {
  description = "Public IP address for admin SSH to the Jenkins agent."
  value       = local.agent_public_ip
}

output "agent_public_cidr" {
  description = "Agent public IP as a /32 CIDR. Add this to EKS API or NodePort allowlists if they are restricted."
  value       = "${local.agent_public_ip}/32"
}

output "agent_ssm_session_command" {
  description = "Command to open an AWS Systems Manager shell session to the agent."
  value       = "aws ssm start-session --region ${var.aws_region} --target ${aws_instance.agent.id}"
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

output "jenkins_agent_ssh_public_key" {
  description = "Public SSH key installed in /home/jenkins/.ssh/authorized_keys on the agent."
  value       = local.jenkins_agent_public_key
}

output "jenkins_agent_ssh_private_key_pem" {
  description = "Private SSH key to add in Jenkins credentials when Terraform generated the key. Null if jenkins_agent_public_key was supplied."
  value       = var.jenkins_agent_public_key == null ? tls_private_key.jenkins_agent[0].private_key_pem : null
  sensitive   = true
}

output "admin_ssh_key_name" {
  description = "EC2 key pair name attached to the controller and agent."
  value       = local.admin_key_name
}

output "admin_ssh_private_key_pem" {
  description = "Private key for admin SSH when Terraform generated the EC2 key pair. Null if ssh_key_name was supplied."
  value       = var.ssh_key_name == null ? tls_private_key.admin[0].private_key_pem : null
  sensitive   = true
}

output "vpc_id" {
  description = "VPC used by the Jenkins controller and agent."
  value       = local.vpc_id
}

output "subnet_id" {
  description = "Subnet used by the Jenkins controller and agent."
  value       = local.subnet_id
}
