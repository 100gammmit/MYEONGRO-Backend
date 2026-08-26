#!/bin/bash
set -euxo pipefail

dnf install -y docker unzip

systemctl enable --now docker
usermod -aG docker ec2-user

install -d -m 755 /usr/libexec/docker/cli-plugins
curl -fSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64" \
  -o /usr/libexec/docker/cli-plugins/docker-compose
chmod +x /usr/libexec/docker/cli-plugins/docker-compose

if ! command -v aws >/dev/null 2>&1; then
  curl -fSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
  unzip -q /tmp/awscliv2.zip -d /tmp
  /tmp/aws/install
  rm -rf /tmp/awscliv2.zip /tmp/aws
fi

# These paths/modes are also hardcoded in deploy/send-ssm-command.sh and
# deploy/deploy.sh (dev branch, not present here) — keep all three in sync by
# hand until the branches converge.
install -d -m 755 /opt/myeongro/releases
install -d -m 700 /opt/myeongro/env
