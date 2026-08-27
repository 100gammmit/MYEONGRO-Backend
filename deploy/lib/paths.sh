# Single source of truth for the backend host's on-disk deploy layout.
#
# Consumers:
#   - deploy/deploy.sh sources this directly after send-ssm-command.sh bundles
#     a copy onto the remote host alongside it, and exports MYEONGRO_ENV_DIR
#     so docker compose can substitute it into compose.prod.yaml.
#   - deploy/compose.prod.yaml reads MYEONGRO_ENV_DIR via docker compose's own
#     ${VAR} substitution (deploy.sh exports it; it's never sourced directly).
#   - deploy/send-ssm-command.sh sources this locally (it runs from a full
#     repo checkout in GitHub Actions) to know what to create on the host
#     before anything else is unpacked.
#   - infra/terraform/templates/user_data.sh.tftpl gets this file's raw text
#     spliced in by Terraform's templatefile() at `terraform apply` time.
#
# All four read the exact same file (directly or via docker compose env
# substitution), so the paths below can't drift out of sync between them.
# Change them here only.
MYEONGRO_DEPLOY_DIR="/opt/myeongro"
MYEONGRO_RELEASES_DIR="$MYEONGRO_DEPLOY_DIR/releases"
MYEONGRO_ENV_DIR="$MYEONGRO_DEPLOY_DIR/env"
