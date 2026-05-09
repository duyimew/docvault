data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name = var.cluster_name
  azs  = slice(data.aws_availability_zones.available.names, 0, 2)

  tags = {
    Project     = "DocVault"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

module "vpc" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-vpc.git?ref=7c1f791efd61f326ed6102d564d1a65d1eceedf0" # v5.21.0

  name = "${local.name}-vpc"
  cidr = "10.20.0.0/16"

  azs             = local.azs
  public_subnets  = ["10.20.1.0/24", "10.20.2.0/24"]
  private_subnets = ["10.20.11.0/24", "10.20.12.0/24"]

  enable_nat_gateway      = var.enable_nat_gateway
  single_nat_gateway      = true
  map_public_ip_on_launch = true

  enable_dns_hostnames = true
  enable_dns_support   = true

  manage_default_security_group  = true
  default_security_group_ingress = []
  default_security_group_egress  = []

  public_subnet_tags = {
    "kubernetes.io/role/elb" = 1
  }

  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = 1
  }

  tags = local.tags
}

module "eks" {
  source = "git::https://github.com/terraform-aws-modules/terraform-aws-eks.git?ref=608c41a295a415f9aeea5c397a9dc123cee6d4c9" # v20.32.0

  cluster_name    = local.name
  cluster_version = var.cluster_version

  cluster_endpoint_public_access       = true
  cluster_endpoint_public_access_cidrs = var.cluster_endpoint_public_access_cidrs

  enable_cluster_creator_admin_permissions = true

  cluster_enabled_log_types = [
    "api",
    "audit",
    "authenticator",
    "controllerManager",
    "scheduler",
  ]

  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent = true
    }
    aws-ebs-csi-driver = {
      most_recent = true
    }
  }

  vpc_id     = module.vpc.vpc_id
  subnet_ids = concat(module.vpc.public_subnets, module.vpc.private_subnets)

  eks_managed_node_groups = {
    docvault = {
      name = "docvault-ng"

      subnet_ids     = var.enable_nat_gateway ? module.vpc.private_subnets : module.vpc.public_subnets
      instance_types = var.node_instance_types
      capacity_type  = "ON_DEMAND"
      ami_type       = "AL2023_x86_64_STANDARD"

      min_size     = var.node_min_size
      max_size     = var.node_max_size
      desired_size = var.node_desired_size

      use_custom_launch_template = true

      iam_role_additional_policies = {
        AmazonEBSCSIDriverPolicy = "arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
      }

      metadata_options = {
        http_endpoint               = "enabled"
        http_tokens                 = "required"
        http_put_response_hop_limit = 2
      }

      block_device_mappings = {
        xvda = {
          device_name = "/dev/xvda"
          ebs = {
            volume_size           = var.node_disk_size
            volume_type           = "gp3"
            encrypted             = true
            delete_on_termination = true
          }
        }
      }

      labels = {
        workload = "docvault"
      }
    }
  }

  tags = local.tags
}

# -- NodePort security group rules ------------------------------------
# Allow external access to web (30006) and keycloak (30080) on worker nodes.
# Gateway stays ClusterIP (accessed via Next.js server-side rewrites).

locals {
  nodeport_rules = {
    web      = { port = 30006, desc = "NodePort: docvault-web" }
    keycloak = { port = 30080, desc = "NodePort: keycloak" }
  }
}

variable "nodeport_access_cidrs" {
  description = "CIDR blocks allowed to reach NodePort services (web, keycloak). Use 0.0.0.0/0 for open access."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

resource "aws_security_group_rule" "nodeport" {
  for_each = local.nodeport_rules

  type              = "ingress"
  from_port         = each.value.port
  to_port           = each.value.port
  protocol          = "tcp"
  cidr_blocks       = var.nodeport_access_cidrs
  description       = each.value.desc
  security_group_id = module.eks.node_security_group_id
}