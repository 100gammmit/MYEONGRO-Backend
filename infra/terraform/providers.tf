provider "aws" {
  region  = var.aws_region
  profile = "myeongro"

  default_tags {
    tags = {
      Project   = var.project_name
      ManagedBy = "terraform"
    }
  }
}
