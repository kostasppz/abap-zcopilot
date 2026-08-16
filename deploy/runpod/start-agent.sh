#!/usr/bin/env bash
set -Eeuo pipefail

AGENT_DIR="${ABAP_AGENT_DIR:-/workspace/abap-stack/abap-agent}"
CHAT_MODEL="${CHAT_MODEL:-abap-expert}"
EMBEDDING_MODEL="${EMBEDDING_MODEL:-embeddinggemma}"

until [[ -f "$AGENT_DIR/agent.py" && -f "$AGENT_DIR/Modelfile" ]]; do
  echo "Waiting for agent.py and Modelfile in $AGENT_DIR"
  sleep 10
done

export OLLAMA_HOST="http://127.0.0.1:11434"
export OLLAMA_MODELS="${OLLAMA_MODELS:-/workspace/abap-stack/data/ollama}"
export CHAT_MODEL EMBEDDING_MODEL
export KNOWLEDGE_DIR="${KNOWLEDGE_DIR:-$AGENT_DIR/knowledge}"
export UPLOAD_DIR="${UPLOAD_DIR:-$KNOWLEDGE_DIR/uploads}"
export DATABASE_DIR="${DATABASE_DIR:-$AGENT_DIR/vector_db}"
export TEMPLATES_DIR="${TEMPLATES_DIR:-$AGENT_DIR/templates}"
export STATIC_DIR="${STATIC_DIR:-$AGENT_DIR/static}"
export CORS_ORIGINS=""

mkdir -p "$KNOWLEDGE_DIR" "$UPLOAD_DIR" "$DATABASE_DIR" "$TEMPLATES_DIR" "$STATIC_DIR"

until /usr/bin/ollama list >/dev/null 2>&1; do
  echo "Waiting for Ollama"
  sleep 3
done

if ! /usr/bin/ollama show "$EMBEDDING_MODEL" >/dev/null 2>&1; then
  /usr/bin/ollama pull "$EMBEDDING_MODEL"
fi

if ! /usr/bin/ollama show "$CHAT_MODEL" >/dev/null 2>&1; then
  /usr/bin/ollama create "$CHAT_MODEL" -f "$AGENT_DIR/Modelfile"
fi

exec /opt/agent-venv/bin/python "$AGENT_DIR/agent.py" web \
  --host 127.0.0.1 \
  --port 8000 \
  --no-browser
