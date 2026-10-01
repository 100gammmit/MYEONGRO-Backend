#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
deploy_dir="$(cd -- "$script_dir/.." && pwd)"
container_name="myeongro-caddy-log-test-$$"

if command -v cygpath >/dev/null 2>&1; then
  deploy_mount_dir="$(cygpath -w "$deploy_dir")"
  export MSYS_NO_PATHCONV=1
else
  deploy_mount_dir="$deploy_dir"
fi

cleanup() {
  docker rm -f "$container_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

caddy_image="$(awk '$1 == "image:" && $2 ~ /^caddy:/ { print $2; exit }' "$deploy_dir/compose.prod.yaml")"
if [[ -z "$caddy_image" ]]; then
  echo "failed to resolve the pinned Caddy image from compose.prod.yaml" >&2
  exit 1
fi

docker run --rm \
  --env API_DOMAIN_NAME=localhost \
  --volume "$deploy_mount_dir/Caddyfile:/etc/caddy/Caddyfile:ro" \
  "$caddy_image" \
  caddy adapt --config /etc/caddy/Caddyfile --adapter caddyfile --validate >/dev/null

docker run --detach \
  --name "$container_name" \
  --env API_DOMAIN_NAME=localhost \
  --publish 127.0.0.1::443 \
  --volume "$deploy_mount_dir/Caddyfile:/etc/caddy/Caddyfile:ro" \
  "$caddy_image" >/dev/null

host_port=""
for _ in $(seq 1 30); do
  host_port="$(docker port "$container_name" 443/tcp 2>/dev/null | awk -F: 'END { print $NF }')"
  if [[ -n "$host_port" ]] && curl --silent --insecure \
    --resolve "localhost:${host_port}:127.0.0.1" \
    "https://localhost:${host_port}/healthz" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if [[ -z "$host_port" ]]; then
  echo "Caddy test container did not expose its HTTPS port" >&2
  docker logs "$container_name" >&2 || true
  exit 1
fi

query_sentinel="private-oauth-code-sentinel"
header_sentinel="private-header-sentinel"
ip_sentinel="203.0.113.77"

curl --silent --show-error --insecure \
  --resolve "localhost:${host_port}:127.0.0.1" \
  --header "X-Log-Sentinel: ${header_sentinel}" \
  --header "X-Forwarded-For: ${ip_sentinel}" \
  "https://localhost:${host_port}/oauth2/code/kakao?code=${query_sentinel}" \
  --output /dev/null || true

sleep 1
runtime_logs="$(docker logs "$container_name" 2>&1)"
error_logs="$(printf '%s\n' "$runtime_logs" | grep -F '"logger":"http.log.error"' || true)"

if [[ -z "$error_logs" ]]; then
  echo "expected an upstream failure in the Caddy runtime log" >&2
  printf '%s\n' "$runtime_logs" >&2
  exit 1
fi

if grep -Fq '"request":' <<<"$error_logs"; then
  echo "Caddy runtime error log retained the request object" >&2
  printf '%s\n' "$error_logs" >&2
  exit 1
fi

for sentinel in "$query_sentinel" "$header_sentinel" "$ip_sentinel"; do
  if grep -Fq "$sentinel" <<<"$runtime_logs"; then
    echo "Caddy runtime logs exposed sentinel: $sentinel" >&2
    exit 1
  fi
done

echo "Caddy runtime log redaction test passed"
