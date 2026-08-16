#!/usr/bin/env bash
set -Eeuo pipefail

: "${GUARDIAN_API_TOKEN:?Set GUARDIAN_API_TOKEN with a RunPod secret}"

until curl -fsS http://127.0.0.1:8000/api/status >/dev/null; do
  echo "Waiting for ABAP Expert RAG service"
  sleep 5
done

export LLM_PROVIDER="abap-agent"
export ABAP_AGENT_BASE_URL="http://127.0.0.1:8000"
export ABAP_AGENT_MODEL="${CHAT_MODEL:-abap-expert}"
export REDACTION_ENABLED="true"
export BUNDLED_KNOWLEDGE_PATH=""
export REQUIRE_API_AUTH="true"
export RATE_LIMIT_PER_MINUTE="${RATE_LIMIT_PER_MINUTE:-60}"
export MAX_SOURCE_LENGTH="${MAX_SOURCE_LENGTH:-200000}"
export MAX_REQUEST_BODY_BYTES="${MAX_REQUEST_BODY_BYTES:-1048576}"
export REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-90}"
export AI_TIMEOUT_SECONDS="${AI_TIMEOUT_SECONDS:-90}"
export MAX_TOKENS="${MAX_TOKENS:-1024}"
export ANALYZER_JAR="/opt/guardian/analyzer.jar"
export JAVA_BIN="/usr/bin/java"

cd /opt/guardian-src
exec /opt/guardian-venv/bin/uvicorn gateway.main:app \
  --host 0.0.0.0 \
  --port 8001 \
  --no-access-log
