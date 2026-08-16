#!/usr/bin/env bash
set -Eeuo pipefail

export OLLAMA_HOST="127.0.0.1:11434"
export OLLAMA_MODELS="${OLLAMA_MODELS:-/workspace/abap-stack/data/ollama}"
mkdir -p "$OLLAMA_MODELS"

exec /usr/bin/ollama serve
