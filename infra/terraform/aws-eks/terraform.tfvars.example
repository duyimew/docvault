aws_region      = "ap-southeast-1"
cluster_name    = "docvault-eks"
cluster_version = "1.35"
environment     = "testing"

# For a stricter setup, replace this with your workstation public IP CIDR,
# for example ["203.0.113.10/32"].
cluster_endpoint_public_access_cidrs = ["0.0.0.0/0"]

node_instance_types = ["t3.large"]
node_desired_size   = 2
node_min_size       = 1
node_max_size       = 3
node_disk_size      = 30

# false = lower-cost MVP, nodes in public subnets.
# true = better practice, nodes in private subnets with NAT Gateway cost.
enable_nat_gateway = false
