#!/usr/bin/env bash
set -Eeuo pipefail

: "${AGENT_WEB_PASSWORD:?Set AGENT_WEB_PASSWORD with a RunPod secret}"

agent_web_username="${AGENT_WEB_USERNAME:-guardian}"

if [[ ! "$agent_web_username" =~ ^[A-Za-z0-9._-]{1,64}$ ]]; then
  echo "AGENT_WEB_USERNAME contains unsupported characters" >&2
  exit 1
fi

if (( ${#AGENT_WEB_PASSWORD} < 24 )); then
  echo "AGENT_WEB_PASSWORD must contain at least 24 characters" >&2
  exit 1
fi

case "$AGENT_WEB_PASSWORD" in
  *$'\n'*|*$'\r'*)
    echo "AGENT_WEB_PASSWORD must not contain line breaks" >&2
    exit 1
    ;;
esac

umask 077
install -d -m 0700 /run/abap-guardian
printf '%s\n' "$AGENT_WEB_PASSWORD" \
  | /usr/bin/htpasswd -ciB \
      /run/abap-guardian/agent-web.htpasswd \
      "$agent_web_username" \
      >/dev/null

/usr/sbin/nginx -t

# RunPod's base script starts Nginx, SSH and optional Jupyter services. Remove
# application secrets from that child so its environment export cannot write
# them to /etc/rp_environment. Supervisor still injects them into Agent and
# Guardian directly.
exec /usr/bin/env \
  -u AGENT_WEB_PASSWORD \
  -u GUARDIAN_API_TOKEN \
  -u ADMIN_API_KEY \
  /start.sh
