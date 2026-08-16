#!/usr/bin/env bash
set -Eeuo pipefail

mkdir -p \
  /workspace/abap-stack/data/ollama \
  /workspace/abap-stack/logs \
  /workspace/abap-stack/secrets

exec /usr/bin/supervisord -n -c /opt/runpod/supervisord.conf
