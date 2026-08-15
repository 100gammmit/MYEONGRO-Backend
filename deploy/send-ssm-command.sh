#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
  echo "usage: send-ssm-command.sh <instance-id> <aws-region> <image-uri> <ssm-env-parameter>" >&2
  exit 2
fi

instance_id="$1"
aws_region="$2"
image_uri="$3"
env_parameter="$4"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
image_tag="${image_uri##*:}"

if [[ ! "$image_tag" =~ ^[0-9a-f]{40}$ ]]; then
  echo "image URI must end with a 40-character lowercase Git SHA tag" >&2
  exit 2
fi

bundle_id="$image_tag-$(date +%s)-$RANDOM"
remote_bundle="/opt/myeongro/releases/$bundle_id"
compose_base64="$(base64 -w 0 "$script_dir/compose.prod.yaml")"
deploy_base64="$(base64 -w 0 "$script_dir/deploy.sh")"
quoted_image="$(printf '%q' "$image_uri")"
quoted_region="$(printf '%q' "$aws_region")"
quoted_parameter="$(printf '%q' "$env_parameter")"
quoted_bundle="$(printf '%q' "$remote_bundle")"

parameters_file="$(mktemp)"
trap 'rm -f "$parameters_file"' EXIT

jq -n \
  --arg compose "$compose_base64" \
  --arg deploy "$deploy_base64" \
  --arg bundle "$remote_bundle" \
  --arg run "$quoted_bundle/deploy.sh $quoted_image $quoted_region $quoted_parameter" \
  '{commands: [
    "install -d -m 755 /opt/myeongro/releases",
    ("install -d -m 755 " + ($bundle | @sh)),
    ("printf %s " + ($compose | @sh) + " | base64 -d > " + ($bundle | @sh) + "/compose.prod.yaml"),
    ("printf %s " + ($deploy | @sh) + " | base64 -d > " + ($bundle | @sh) + "/deploy.sh"),
    ("chmod 700 " + ($bundle | @sh) + "/deploy.sh"),
    $run
  ], executionTimeout: ["600"]}' > "$parameters_file"

command_id="$(aws ssm send-command \
  --region "$aws_region" \
  --instance-ids "$instance_id" \
  --document-name AWS-RunShellScript \
  --comment "Deploy MYEONGRO $image_tag" \
  --timeout-seconds 660 \
  --parameters "file://$parameters_file" \
  --query 'Command.CommandId' \
  --output text)"

echo "SSM command: $command_id"

get_status() {
  aws ssm get-command-invocation \
    --region "$aws_region" \
    --command-id "$command_id" \
    --instance-id "$instance_id" \
    --query 'Status' \
    --output text 2>/dev/null || true
}

is_terminal() {
  case "$1" in
    Success|Cancelled|Failed|TimedOut|Undeliverable|Terminated)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

command_status="Pending"
for _ in $(seq 1 120); do
  command_status="$(get_status)"
  if is_terminal "$command_status"; then
    break
  fi
  sleep 5
done

if ! is_terminal "$command_status"; then
  echo "SSM command exceeded 10 minutes; requesting cancellation" >&2
  aws ssm cancel-command \
    --region "$aws_region" \
    --command-id "$command_id" \
    --instance-ids "$instance_id"

  for _ in $(seq 1 60); do
    command_status="$(get_status)"
    if is_terminal "$command_status"; then
      break
    fi
    sleep 5
  done
fi

aws ssm get-command-invocation \
  --region "$aws_region" \
  --command-id "$command_id" \
  --instance-id "$instance_id" \
  --query '{Status:Status,StandardOutput:StandardOutputContent,StandardError:StandardErrorContent}' || true

if ! is_terminal "$command_status"; then
  echo "SSM command cancellation did not reach a terminal state: $command_status" >&2
  exit 1
fi

if [[ "$command_status" != "Success" ]]; then
  exit 1
fi
