# The real value is populated out-of-band after apply:
#   aws ssm put-parameter --name <name> --type SecureString --overwrite --value "$(cat backend.env)"
# Terraform only owns the parameter's existence, not its value.
resource "aws_ssm_parameter" "backend_env" {
  name   = local.ssm_backend_env_name
  type   = "SecureString"
  value  = "CHANGE_ME"
  key_id = data.aws_kms_key.ssm.key_id

  lifecycle {
    ignore_changes = [value]
  }
}
