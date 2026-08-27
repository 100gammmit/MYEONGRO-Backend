#!/usr/bin/env sh
set -eu

if [ "$#" -ne 3 ]; then
  echo "usage: deploy.sh <image-uri> <aws-region> <ssm-env-parameter>" >&2
  exit 2
fi

target_image="$1"
aws_region="$2"
env_parameter="$3"
bundle_dir="$(CDPATH= cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/paths.sh
. "$bundle_dir/paths.sh"
deploy_dir="$MYEONGRO_DEPLOY_DIR"
compose_file="$bundle_dir/compose.prod.yaml"
env_dir="$deploy_dir/env"
env_file="$env_dir/backend.env"
previous_env_file="$env_dir/backend.env.previous"
current_image_file="$deploy_dir/current-image"
lock_file="$deploy_dir/deploy.lock"
temp_env="$env_dir/backend.env.new"
previous_image=""
env_changed=0
cutover_started=0
deployment_committed=0

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
    IMAGE_URI="$previous_image" docker compose -f "$compose_file" pull api || true
    IMAGE_URI="$previous_image" docker compose -f "$compose_file" up -d redis || return 1
    IMAGE_URI="$previous_image" docker compose -f "$compose_file" up -d --no-deps api || return 1
    wait_until_ready || return 1
    printf '%s\n' "$previous_image" > "$current_image_file"
    chmod 600 "$current_image_file"
    echo "rollback succeeded" >&2
    return 0
  fi

  failed_container="$(running_api_container)"
  if [ -n "$failed_container" ]; then
    echo "removing failed first-deployment container: $failed_container" >&2
    docker rm -f "$failed_container" >/dev/null || return 1
  fi
  rm -f "$current_image_file"
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

  rm -f "$temp_env"
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [ -f "$current_image_file" ]; then
  previous_image="$(cat "$current_image_file")"
else
  current_container="$(running_api_container)"
  if [ -n "$current_container" ]; then
    previous_image="$(docker inspect --format '{{.Config.Image}}' "$current_container")"
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

IMAGE_URI="$target_image" docker compose -f "$compose_file" pull api
IMAGE_URI="$target_image" docker compose -f "$compose_file" up -d redis

cutover_started=1
IMAGE_URI="$target_image" docker compose -f "$compose_file" up -d --no-deps api

if ! wait_until_ready; then
  echo "deployment health check failed: $target_image" >&2
  exit 1
fi

printf '%s\n' "$target_image" > "$current_image_file"
chmod 600 "$current_image_file"
deployment_committed=1
env_changed=0
cutover_started=0
rm -f "$previous_env_file"
echo "deployment succeeded: $target_image"
