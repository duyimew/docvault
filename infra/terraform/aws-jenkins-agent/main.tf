data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name = "name"
    values = [
      "ubuntu/images/hvm-ssd/ubuntu-noble-24.04-amd64-server-*",
      "ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*",
    ]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "tls_private_key" "jenkins_agent" {
  count     = var.jenkins_agent_public_key == null ? 1 : 0
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "tls_private_key" "admin" {
  count     = var.ssh_key_name == null ? 1 : 0
  algorithm = "RSA"
  rsa_bits  = 4096
}

locals {
  name = "${var.name_prefix}-${var.environment}"

  tags = merge(
    {
      Project     = "DocVault"
      Environment = var.environment
      Component   = "JenkinsAgent"
      ManagedBy   = "Terraform"
    },
    var.extra_tags,
  )

  availability_zone        = var.availability_zone != null ? var.availability_zone : data.aws_availability_zones.available.names[0]
  vpc_id                   = var.create_network ? aws_vpc.this[0].id : var.vpc_id
  subnet_id                = var.create_network ? aws_subnet.public[0].id : var.subnet_id
  admin_key_name           = var.ssh_key_name != null ? var.ssh_key_name : aws_key_pair.admin[0].key_name
  jenkins_agent_public_key = var.jenkins_agent_public_key != null ? var.jenkins_agent_public_key : tls_private_key.jenkins_agent[0].public_key_openssh
  ssh_cidr_blocks          = distinct(concat(var.controller_cidr_blocks, var.admin_cidr_blocks))
  agent_profile_name       = var.agent_iam_instance_profile != null ? var.agent_iam_instance_profile : (var.create_ssm_instance_profile ? aws_iam_instance_profile.ssm[0].name : null)
  agent_public_ip          = var.create_agent_eip ? aws_eip.agent[0].public_ip : aws_instance.agent.public_ip
}

resource "aws_key_pair" "admin" {
  count = var.ssh_key_name == null ? 1 : 0

  key_name   = "${local.name}-admin"
  public_key = tls_private_key.admin[0].public_key_openssh

  tags = merge(local.tags, {
    Name = "${local.name}-admin"
  })
}

data "aws_iam_policy_document" "ec2_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ssm" {
  count = var.create_ssm_instance_profile ? 1 : 0

  name               = "${local.name}-ssm"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume_role.json

  tags = merge(local.tags, {
    Name = "${local.name}-ssm"
  })
}

resource "aws_iam_role_policy_attachment" "ssm" {
  count = var.create_ssm_instance_profile ? 1 : 0

  role       = aws_iam_role.ssm[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ssm" {
  count = var.create_ssm_instance_profile ? 1 : 0

  name = "${local.name}-ssm"
  role = aws_iam_role.ssm[0].name

  tags = merge(local.tags, {
    Name = "${local.name}-ssm"
  })
}

resource "aws_vpc" "this" {
  count = var.create_network ? 1 : 0

  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.tags, {
    Name = "${local.name}-vpc"
  })
}

resource "aws_internet_gateway" "this" {
  count = var.create_network ? 1 : 0

  vpc_id = aws_vpc.this[0].id

  tags = merge(local.tags, {
    Name = "${local.name}-igw"
  })
}

resource "aws_subnet" "public" {
  count = var.create_network ? 1 : 0

  vpc_id                  = aws_vpc.this[0].id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = local.availability_zone
  map_public_ip_on_launch = true

  tags = merge(local.tags, {
    Name = "${local.name}-public-a"
  })
}

resource "aws_route_table" "public" {
  count = var.create_network ? 1 : 0

  vpc_id = aws_vpc.this[0].id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this[0].id
  }

  tags = merge(local.tags, {
    Name = "${local.name}-public-rt"
  })
}

resource "aws_route_table_association" "public" {
  count = var.create_network ? 1 : 0

  subnet_id      = aws_subnet.public[0].id
  route_table_id = aws_route_table.public[0].id
}

resource "aws_security_group" "agent" {
  name        = "${local.name}-sg"
  description = "Jenkins agent SSH ingress"
  vpc_id      = local.vpc_id

  tags = merge(local.tags, {
    Name = "${local.name}-sg"
  })
}

resource "aws_vpc_security_group_ingress_rule" "agent_ssh" {
  for_each = toset(local.ssh_cidr_blocks)

  security_group_id = aws_security_group.agent.id
  cidr_ipv4         = each.value
  from_port         = 22
  ip_protocol       = "tcp"
  to_port           = 22
  description       = "SSH from local Jenkins controller/admin"
}

resource "aws_vpc_security_group_egress_rule" "agent_all" {
  security_group_id = aws_security_group.agent.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
  description       = "Allow agent outbound"
}

resource "aws_instance" "agent" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.agent_instance_type
  subnet_id                   = local.subnet_id
  vpc_security_group_ids      = [aws_security_group.agent.id]
  associate_public_ip_address = var.associate_public_ip_address
  key_name                    = local.admin_key_name
  iam_instance_profile        = local.agent_profile_name
  user_data_replace_on_change = true

  user_data = templatefile("${path.module}/templates/agent-user-data.sh.tftpl", {
    agent_workspace          = var.jenkins_agent_workspace
    jenkins_agent_public_key = local.jenkins_agent_public_key
    kubectl_version          = var.kubectl_version
  })

  root_block_device {
    volume_size = var.agent_root_volume_size
    volume_type = "gp3"
    encrypted   = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = merge(local.tags, {
    Name = local.name
    Role = "jenkins-agent"
  })
}

resource "aws_eip" "agent" {
  count = var.create_agent_eip ? 1 : 0

  domain   = "vpc"
  instance = aws_instance.agent.id

  tags = merge(local.tags, {
    Name = "${local.name}-eip"
  })

  depends_on = [aws_internet_gateway.this]
}
