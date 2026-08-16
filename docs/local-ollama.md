# Local Ollama Setup

This setup is optional and intended for local development or organizations
that do not permit source or prompts to leave their network. Normal Eclipse
users of the hosted release do not install Ollama.

ABAP Guardian's AI features run entirely on your machine through
[Ollama](https://ollama.com). No cloud account is required.

## Install & pull the default model

```bash
# macOS
brew install ollama
# Linux
curl -fsSL https://ollama.com/install.sh | sh

ollama serve                # if not already running as a service
ollama pull gemma4:e4b      # default model used by the gateway
```

## Configuration

The gateway reads:

| Variable | Default | Meaning |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `gemma4:e4b` | Model used for enhancement/explain/fix |
| `AI_TIMEOUT_SECONDS` | `60` | Per-AI-request timeout |
| `MAX_TOKENS` | `2048` | Generation cap |

Check availability:

```bash
curl -s localhost:8000/health          # llmAvailable and ollamaAvailable
curl -s localhost:8000/api/v1/models   # models known to Ollama
```

## Choosing another model

Any Ollama model works; smaller models are faster but produce weaker
explanations. Suggested alternatives: `llama3.1:8b`, `qwen2.5-coder:7b`.

```bash
ollama pull qwen2.5-coder:7b
OLLAMA_MODEL=qwen2.5-coder:7b uvicorn gateway.main:app --port 8000
```

## Without Ollama

Everything still works: the gateway returns deterministic findings and marks
`aiEnhanced: false`. `/explain` and `/suggest-fix` return HTTP 503.

## Existing ABAP Expert RAG service

If Ollama is already used through the local ABAP Expert service with Chroma,
PDF and Word knowledge, do not bypass that retrieval pipeline. Configure
Guardian with `LLM_PROVIDER=abap-agent` and follow
[`local-abap-agent.md`](local-abap-agent.md).

## External providers (opt-in, discouraged)

External AI providers are disabled by default. If you explicitly set
`ALLOW_EXTERNAL_PROVIDERS=true` you accept that prompts (never full source
beyond the bounded snippet) leave your machine; the redaction layer stays
active and masks likely personal data and credentials. Read
`docs/privacy-model.md` first.
