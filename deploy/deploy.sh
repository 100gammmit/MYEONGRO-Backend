#!/usr/bin/env sh
set -eu

if [ "$#" -ne 3 ]; then
  echo "usage: deploy.sh <image-uri> <aws-region> <ssm-env-parameter>" >&2
  exit 2
fi

target_image="$1"
aws_region="$2"
env_parameter="$3"
deploy_dir="/opt/myeongro"
compose_file="$deploy_dir/compose.prod.yaml"
env_dir="$deploy_dir/env"
env_file="$env_dir/backend.env"
previous_env_file="$env_dir/backend.env.previous"
current_image_file="$deploy_dir/current-image"
lock_dir="$deploy_dir/deploy.lock"
temp_env="$env_dir/backend.env.new"
env_changed=0

mkdir -p "$env_dir"
chmod 700 "$env_dir"

if ! mkdir "$lock_dir" 2>/dev/null; then
  echo "another deployment is already running" >&2
  exit 1
fi

cleanup() {
  status="$?"
  if [ "$status" -ne 0 ] && [ "$env_changed" -eq 1 ]; then
    if [ -f "$previous_env_file" ]; then
      mv "$previous_env_file" "$env_file"
      chmod 600 "$env_file"
    else
      rm -f "$env_file"
    fi
  fi
  rm -f "$temp_env"
  rmdir "$lock_dir" 2>/dev/null || true
  trap - EXIT INT TERM
  exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

previous_image=""
if [ -f "$current_image_file" ]; then
  previous_image="$(cat "$current_image_file")"
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
aws ecr get-login-password --region "$aws_region" \
  | docker login --username AWS --password-stdin "$registry"

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

start_image() {
  image_uri="$1"
  IMAGE_URI="$image_uri" docker compose -f "$compose_file" pull api || return 1
  IMAGE_URI="$image_uri" docker compose -f "$compose_file" up -d redis || return 1
  IMAGE_URI="$image_uri" docker compose -f "$compose_file" up -d --no-deps api || return 1
  wait_until_ready
}

if start_image "$target_image"; then
  printf '%s\n' "$target_image" > "$current_image_file"
  chmod 600 "$current_image_file"
  rm -f "$previous_env_file"
  env_changed=0
  echo "deployment succeeded: $target_image"
  exit 0
fi

echo "deployment health check failed: $target_image" >&2

if [ -n "$previous_image" ]; then
  if [ -f "$previous_env_file" ]; then
    mv "$previous_env_file" "$env_file"
    chmod 600 "$env_file"
  else
    rm -f "$env_file"
  fi
  env_changed=0

  echo "rolling back to: $previous_image" >&2
  if start_image "$previous_image"; then
    printf '%s\n' "$previous_image" > "$current_image_file"
    echo "rollback succeeded" >&2
  else
    echo "rollback failed" >&2
  fi
fi

exit 1
