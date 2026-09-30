#!/usr/bin/env bash
set -euo pipefail

test_root="$(mktemp -d)"
trap 'rm -rf "$test_root"' EXIT

deploy_root="$test_root/opt/myeongro"
releases_dir="$deploy_root/releases"
env_dir="$deploy_root/env"
old_bundle="$releases_dir/old-release"
new_bundle="$releases_dir/new-release"
mock_bin="$test_root/bin"
docker_log="$test_root/docker.log"
mkdir -p "$old_bundle" "$new_bundle" "$env_dir" "$mock_bin"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cp "$script_dir/../deploy.sh" "$new_bundle/deploy.sh"
cp "$script_dir/../compose.prod.yaml" "$new_bundle/compose.prod.yaml"
cp "$script_dir/../Caddyfile" "$new_bundle/Caddyfile"
printf '%s\n' 'old compose marker' > "$old_bundle/compose.prod.yaml"
printf '%s\n' 'old caddy marker' > "$old_bundle/Caddyfile"

cat > "$new_bundle/paths.sh" <<EOF
MYEONGRO_DEPLOY_DIR="$deploy_root"
MYEONGRO_RELEASES_DIR="$releases_dir"
MYEONGRO_ENV_DIR="$env_dir"
EOF

old_image='123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/backend:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
new_image='123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/backend:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
printf '%s\n%s\n%s\n%s\n%s\n' \
  "$old_image" "$old_bundle" 'ap-northeast-2' '/old/log-group' 'old.example.com' \
  > "$deploy_root/current-release"
cp "$deploy_root/current-release" "$test_root/expected-release"
printf '%s\n' 'OLD_ENV=1' > "$env_dir/backend.env"

cat > "$mock_bin/aws" <<'EOF'
#!/usr/bin/env sh
if [ "$1 $2" = "ssm get-parameter" ]; then
  printf '%s\n' 'NEW_ENV=1'
elif [ "$1 $2" = "ecr get-login-password" ]; then
  printf '%s\n' 'test-password'
else
  echo "unexpected aws invocation: $*" >&2
  exit 1
fi
EOF

cat > "$mock_bin/docker" <<'EOF'
#!/usr/bin/env sh
printf '%s|%s|%s|%s|%s|%s\n' \
  "${MYEONGRO_BUNDLE_DIR:-}" "${AWS_REGION:-}" "${CLOUDWATCH_LOG_GROUP:-}" \
  "${API_DOMAIN_NAME:-}" "${IMAGE_URI:-}" "$*" >> "$TEST_DOCKER_LOG"
if [ "$1" = "login" ]; then
  cat >/dev/null
fi
EOF

cat > "$mock_bin/curl" <<'EOF'
#!/usr/bin/env sh
for argument in "$@"; do
  url="$argument"
done
case "$url" in
  http://127.0.0.1:8080/actuator/health|https://old.example.com/healthz) exit 0 ;;
  https://new.example.com/healthz) exit 1 ;;
  *) echo "unexpected curl URL: $url" >&2; exit 1 ;;
esac
EOF

cat > "$mock_bin/flock" <<'EOF'
#!/usr/bin/env sh
exit 0
EOF

cat > "$mock_bin/sleep" <<'EOF'
#!/usr/bin/env sh
exit 0
EOF

chmod +x "$new_bundle/deploy.sh" "$mock_bin"/*
export PATH="$mock_bin:$PATH"
export TEST_DOCKER_LOG="$docker_log"

set +e
"$new_bundle/deploy.sh" \
  "$new_image" \
  'ap-northeast-2' \
  '/myeongro/production/backend/env' \
  '/new/log-group' \
  'new.example.com'
deploy_status="$?"
set -e

if [ "$deploy_status" -eq 0 ]; then
  echo 'expected the new public health check to fail' >&2
  exit 1
fi

grep -qx 'OLD_ENV=1' "$env_dir/backend.env"
cmp "$test_root/expected-release" "$deploy_root/current-release"
grep -Fq "$old_bundle|ap-northeast-2|/old/log-group|old.example.com|$old_image|compose -f $old_bundle/compose.prod.yaml up -d --no-deps caddy" "$docker_log"

echo 'deploy rollback regression test passed'
