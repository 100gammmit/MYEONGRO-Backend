#!/usr/bin/env sh
set -eu

if [ "$#" -ne 5 ]; then
  echo "usage: deploy.sh <image-uri> <aws-region> <ssm-env-parameter> <cloudwatch-log-group> <api-domain-name>" >&2
  exit 2
fi

target_image="$1"
aws_region="$2"
env_parameter="$3"
cloudwatch_log_group="$4"
api_domain_name="$5"
bundle_dir="$(CDPATH= cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/paths.sh
. "$bundle_dir/paths.sh"
deploy_dir="$MYEONGRO_DEPLOY_DIR"
compose_file="$bundle_dir/compose.prod.yaml"
env_dir="$MYEONGRO_ENV_DIR"
# compose.prod.yaml's env_file path is ${MYEONGRO_ENV_DIR}-substituted by
# docker compose, not hardcoded -- export so every compose invocation below
# picks it up.
export MYEONGRO_ENV_DIR
export MYEONGRO_BUNDLE_DIR="$bundle_dir"
export AWS_REGION="$aws_region"
export CLOUDWATCH_LOG_GROUP="$cloudwatch_log_group"
export API_DOMAIN_NAME="$api_domain_name"
env_file="$env_dir/backend.env"
previous_env_file="$env_dir/backend.env.previous"
release_state_file="$deploy_dir/current-release"
temp_release_state="$deploy_dir/current-release.new"
lock_file="$deploy_dir/deploy.lock"
temp_env="$env_dir/backend.env.new"
previous_image=""
previous_bundle_dir=""
previous_aws_region=""
previous_log_group=""
previous_api_domain=""
env_changed=0
cutover_started=0
deployment_committed=0

validate_domain() {
  printf '%s' "$1" | grep -Eq '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$'
}

validate_log_group() {
  case "$1" in
    /*) return 0 ;;
    *) return 1 ;;
  esac
}

if ! validate_domain "$api_domain_name"; then
  echo "api domain name must be a lowercase fully-qualified domain name" >&2
  exit 2
fi

if ! validate_log_group "$cloudwatch_log_group"; then
  echo "CloudWatch log group must start with /" >&2
  exit 2
fi

mkdir -p "$env_dir"
chmod 700 "$env_dir"

exec 9>"$lock_file"
if ! flock -n 9; then
  echo "another deployment is already running" >&2
  exit 1
fi

restore_previous_env() {
  if [ "$env_changed" -eq 0 ]; then
    return
  fi

  if [ -f "$previous_env_file" ]; then
    mv "$previous_env_file" "$env_file"
    chmod 600 "$env_file"
  else
    rm -f "$env_file"
  fi
  env_changed=0
}

wait_until_ready() {
  attempts=0
  until curl --fail --silent --show-error \
    http://127.0.0.1:8080/actuator/health >/dev/null; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 18 ]; then
      return 1
    fi
    sleep 5
  done
}

wait_until_public_ready() {
  public_domain="$1"
  attempts=0
  until curl --fail --silent --show-error "https://$public_domain/healthz" >/dev/null; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 24 ]; then
      return 1
    fi
    sleep 5
  done
}

running_api_container() {
  docker ps -a \
    --filter "label=com.docker.compose.project=myeongro" \
    --filter "label=com.docker.compose.service=api" \
    --format '{{.ID}}' \
    | head -n 1
}

rollback_runtime() {
  restore_previous_env

  if [ -n "$previous_image" ]; then
    echo "rolling back to: $previous_image" >&2
    previous_compose_file="$previous_bundle_dir/compose.prod.yaml"
    MYEONGRO_BUNDLE_DIR="$previous_bundle_dir" AWS_REGION="$previous_aws_region" \
      CLOUDWATCH_LOG_GROUP="$previous_log_group" API_DOMAIN_NAME="$previous_api_domain" \
      IMAGE_URI="$previous_image" docker compose -f "$previous_compose_file" pull api redis caddy || true
    MYEONGRO_BUNDLE_DIR="$previous_bundle_dir" AWS_REGION="$previous_aws_region" \
      CLOUDWATCH_LOG_GROUP="$previous_log_group" API_DOMAIN_NAME="$previous_api_domain" \
      IMAGE_URI="$previous_image" docker compose -f "$previous_compose_file" up -d redis || return 1
    MYEONGRO_BUNDLE_DIR="$previous_bundle_dir" AWS_REGION="$previous_aws_region" \
      CLOUDWATCH_LOG_GROUP="$previous_log_group" API_DOMAIN_NAME="$previous_api_domain" \
      IMAGE_URI="$previous_image" docker compose -f "$previous_compose_file" up -d --no-deps api || return 1
    wait_until_ready || return 1
    MYEONGRO_BUNDLE_DIR="$previous_bundle_dir" AWS_REGION="$previous_aws_region" \
      CLOUDWATCH_LOG_GROUP="$previous_log_group" API_DOMAIN_NAME="$previous_api_domain" \
      IMAGE_URI="$previous_image" docker compose -f "$previous_compose_file" up -d --no-deps caddy || return 1
    wait_until_public_ready "$previous_api_domain" || return 1
    echo "rollback succeeded" >&2
    return 0
  fi

  failed_container="$(running_api_container)"
  IMAGE_URI="$target_image" docker compose -f "$compose_file" stop caddy >/dev/null 2>&1 || true
  if [ -n "$failed_container" ]; then
    echo "removing failed first-deployment container: $failed_container" >&2
    docker rm -f "$failed_container" >/dev/null || return 1
  fi
}

cleanup() {
  status="$?"
  trap - EXIT INT TERM
  trap '' INT TERM

  if [ "$status" -ne 0 ] && [ "$deployment_committed" -eq 0 ]; then
    if [ "$cutover_started" -eq 1 ]; then
      if ! rollback_runtime; then
        echo "rollback failed" >&2
      fi
    else
      restore_previous_env
    fi
  fi

  rm -f "$temp_env" "$temp_release_state"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [ -f "$release_state_file" ]; then
  previous_image="$(sed -n '1p' "$release_state_file")"
  previous_bundle_dir="$(sed -n '2p' "$release_state_file")"
  previous_aws_region="$(sed -n '3p' "$release_state_file")"
  previous_log_group="$(sed -n '4p' "$release_state_file")"
  previous_api_domain="$(sed -n '5p' "$release_state_file")"

  case "$previous_bundle_dir" in
    "$MYEONGRO_RELEASES_DIR"/*) ;;
    *)
      echo "invalid previous release bundle path" >&2
      exit 1
      ;;
  esac
  if [ -z "$previous_image" ] || [ ! -f "$previous_bundle_dir/compose.prod.yaml" ] || \
    [ ! -f "$previous_bundle_dir/Caddyfile" ] || ! validate_log_group "$previous_log_group" || \
    ! validate_domain "$previous_api_domain"; then
    echo "previous release state is incomplete; refusing an unsafe cutover" >&2
    exit 1
  fi
else
  current_container="$(running_api_container)"
  if [ -n "$current_container" ]; then
    echo "a running API has no release state; refusing an unsafe cutover" >&2
    exit 1
  fi
fi

if [ -f "$env_file" ]; then
  cp "$env_file" "$previous_env_file"
  chmod 600 "$previous_env_file"
else
  rm -f "$previous_env_file"
fi

aws ssm get-parameter \
  --region "$aws_region" \
  --name "$env_parameter" \
  --with-decryption \
  --query 'Parameter.Value' \
  --output text > "$temp_env"

if [ ! -s "$temp_env" ]; then
  echo "SSM environment parameter is empty" >&2
  exit 1
fi

chmod 600 "$temp_env"
mv "$temp_env" "$env_file"
env_changed=1

registry="${target_image%%/*}"
ecr_password="$(aws ecr get-login-password --region "$aws_region")"
printf '%s' "$ecr_password" \
  | docker login --username AWS --password-stdin "$registry"
unset ecr_password

IMAGE_URI="$target_image" docker compose -f "$compose_file" pull api redis caddy
IMAGE_URI="$target_image" docker compose -f "$compose_file" up -d redis

cutover_started=1
IMAGE_URI="$target_image" docker compose -f "$compose_file" up -d --no-deps api

if ! wait_until_ready; then
  echo "deployment health check failed: $target_image" >&2
  exit 1
fi

IMAGE_URI="$target_image" docker compose -f "$compose_file" up -d --no-deps caddy

if ! wait_until_public_ready "$api_domain_name"; then
  echo "public HTTPS health check failed: https://$api_domain_name/healthz" >&2
  exit 1
fi

printf '%s\n%s\n%s\n%s\n%s\n' \
  "$target_image" \
  "$bundle_dir" \
  "$aws_region" \
  "$cloudwatch_log_group" \
  "$api_domain_name" > "$temp_release_state"
chmod 600 "$temp_release_state"
mv "$temp_release_state" "$release_state_file"
deployment_committed=1
env_changed=0
cutover_started=0
rm -f "$previous_env_file"
echo "deployment succeeded: $target_image"
