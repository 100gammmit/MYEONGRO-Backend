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

compose_base64="$(base64 -w 0 "$script_dir/compose.prod.yaml")"
deploy_base64="$(base64 -w 0 "$script_dir/deploy.sh")"
quoted_image="$(printf '%q' "$image_uri")"
quoted_region="$(printf '%q' "$aws_region")"
quoted_parameter="$(printf '%q' "$env_parameter")"

parameters_file="$(mktemp)"
trap 'rm -f "$parameters_file"' EXIT

jq -n \
  --arg compose "$compose_base64" \
  --arg deploy "$deploy_base64" \
  --arg run "/opt/myeongro/deploy.sh $quoted_image $quoted_region $quoted_parameter" \
  '{commands: [
    "install -d -m 755 /opt/myeongro",
    ("printf %s " + ($compose | @sh) + " | base64 -d > /opt/myeongro/compose.prod.yaml"),
    ("printf %s " + ($deploy | @sh) + " | base64 -d > /opt/myeongro/deploy.sh"),
    "chmod 700 /opt/myeongro/deploy.sh",
    $run
  ]}' > "$parameters_file"

command_id="$(aws ssm send-command \
  --region "$aws_region" \
  --instance-ids "$instance_id" \
  --document-name AWS-RunShellScript \
  --comment "Deploy MYEONGRO backend $image_uri" \
  --parameters "file://$parameters_file" \
  --query 'Command.CommandId' \
  --output text)"

echo "SSM command: $command_id"

wait_status=1
command_status="Pending"
for _ in $(seq 1 120); do
  command_status="$(aws ssm get-command-invocation \
    --region "$aws_region" \
    --command-id "$command_id" \
    --instance-id "$instance_id" \
    --query 'Status' \
    --output text 2>/dev/null || true)"

  case "$command_status" in
    Success)
      wait_status=0
      break
      ;;
    Cancelled|Cancelling|Failed|TimedOut|Undeliverable|Terminated)
      break
      ;;
  esac

  sleep 5
done

aws ssm get-command-invocation \
  --region "$aws_region" \
  --command-id "$command_id" \
  --instance-id "$instance_id" \
  --query '{Status:Status,StandardOutput:StandardOutputContent,StandardError:StandardErrorContent}'

if [[ "$command_status" == "Pending" || "$command_status" == "InProgress" || "$command_status" == "Delayed" ]]; then
  echo "SSM command did not finish within 10 minutes" >&2
fi

exit "$wait_status"
