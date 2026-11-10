#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
deploy_dir="$(cd -- "$script_dir/.." && pwd)"
suffix="$$"
network_name="myeongro-caddy-ip-test-$suffix"
proxy_name="myeongro-caddy-ip-proxy-$suffix"
upstream_name="myeongro-caddy-ip-upstream-$suffix"
curl_sink=/dev/null

if command -v cygpath >/dev/null 2>&1; then
  deploy_mount_dir="$(cygpath -w "$deploy_dir")"
  export MSYS_NO_PATHCONV=1
  curl_sink=NUL
else
  deploy_mount_dir="$deploy_dir"
fi

cleanup() {
  docker rm -f "$proxy_name" "$upstream_name" >/dev/null 2>&1 || true
  docker network rm "$network_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

caddy_image="$(awk '$1 == "image:" && $2 ~ /^caddy:/ { print $2; exit }' "$deploy_dir/compose.prod.yaml")"
if [[ -z "$caddy_image" ]]; then
  echo "failed to resolve the pinned Caddy image from compose.prod.yaml" >&2
  exit 1
fi

docker network create "$network_name" >/dev/null

# Stands in for the API and echoes the headers the production proxy forwarded.
upstream_config=':8080 {
	respond /api/tarot/daily-card-selections "{http.request.body}"
	respond "forwarded=[{header.Forwarded}] real-ip=[{header.X-Real-IP}] prefix=[{header.X-Forwarded-Prefix}] port=[{header.X-Forwarded-Port}] ssl=[{header.X-Forwarded-Ssl}] xff=[{header.X-Forwarded-For}]"
}'
docker run --detach \
  --name "$upstream_name" \
  --network "$network_name" \
  --network-alias api \
  --env "UPSTREAM_CONFIG=$upstream_config" \
  "$caddy_image" \
  sh -c 'printf "%s\n" "$UPSTREAM_CONFIG" > /tmp/Caddyfile && exec caddy run --config /tmp/Caddyfile --adapter caddyfile' >/dev/null

docker run --detach \
  --name "$proxy_name" \
  --network "$network_name" \
  --env API_DOMAIN_NAME=localhost \
  --publish 127.0.0.1::443 \
  --volume "$deploy_mount_dir/Caddyfile:/etc/caddy/Caddyfile:ro" \
  "$caddy_image" >/dev/null

spoofed_ip="203.0.113.77"
response=""
for _ in $(seq 1 30); do
  host_port="$(docker port "$proxy_name" 443/tcp 2>/dev/null | awk -F: 'END { print $NF }' || true)"
  if [[ -n "$host_port" ]]; then
    response="$(curl --silent --insecure \
      --resolve "localhost:${host_port}:127.0.0.1" \
      --header "Forwarded: for=${spoofed_ip}" \
      --header "X-Real-IP: ${spoofed_ip}" \
      --header "X-Forwarded-For: ${spoofed_ip}" \
      --header "X-Forwarded-Prefix: /oauth2/authorization" \
      --header "X-Forwarded-Port: 8443" \
      --header "X-Forwarded-Ssl: off" \
      "https://localhost:${host_port}/oauth2/authorization/kakao" || true)"
    [[ "$response" == forwarded=* ]] && break
  fi
  sleep 1
done

if [[ "$response" != forwarded=* ]]; then
  echo "Caddy test proxy did not reach the echo upstream: $response" >&2
  docker logs "$proxy_name" >&2 || true
  exit 1
fi

if grep -Fq "$spoofed_ip" <<<"$response"; then
  echo "Caddy forwarded a client-supplied IP header: $response" >&2
  exit 1
fi

if ! grep -Fq 'prefix=[] port=[] ssl=[]' <<<"$response"; then
  echo "Caddy forwarded a client-supplied path or URL header: $response" >&2
  exit 1
fi

if ! grep -Eq 'xff=\[[0-9a-fA-F:.]+\]' <<<"$response"; then
  echo "Caddy did not set X-Forwarded-For: $response" >&2
  exit 1
fi

echo "Caddy client IP test passed"

body_status() {
  local bytes="$1"
  shift
  printf '%*s{}' "$((bytes - 2))" '' | curl --silent --show-error --insecure --http1.1 \
    --max-time 10 --output "$curl_sink" --write-out '%{http_code}' \
    --resolve "localhost:${host_port}:127.0.0.1" \
    --header "Content-Type: application/json" --data-binary @- "$@" \
    "https://localhost:${host_port}/api/tarot/daily-card-selections"
}

check_body_status() {
  local expected="$1"
  shift
  local actual
  actual="$(body_status "$@")"
  if [[ "$actual" != "$expected" ]]; then
    echo "Caddy returned $actual instead of $expected for body test: $*" >&2
    exit 1
  fi
}

check_body_status 200 32768
check_body_status 413 32769
check_body_status 200 32768 --header 'Transfer-Encoding: chunked'
check_body_status 413 32769 --header 'Transfer-Encoding: chunked'
echo "Caddy request body limit test passed"
