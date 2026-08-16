# ABAP Guardian AI Gateway

FastAPI service that runs the deterministic ABAP Guardian analyzer first and
optionally enriches findings through the private RunPod ABAP Expert streaming
RAG service or its local Ollama runtime. See the repository root `README.md`
and `docs/` for full documentation.

## Local ABAP Expert RAG provider

```text
LLM_PROVIDER=abap-agent
ABAP_AGENT_BASE_URL=http://abap-ai:8000
ABAP_AGENT_MODEL=abap-expert
```

The adapter consumes `POST /api/chat` NDJSON events and exposes the normal
Guardian JSON API to Eclipse. See `docs/local-abap-agent.md` for the combined
Compose setup.

For an authenticated public deployment on a dedicated NVIDIA GPU VM, including
TLS, isolated Docker networking and Eclipse Secure Storage, follow
`docs/dedicated-gpu-vm.md`.

## Public API authentication

- `GUARDIAN_API_TOKEN` or `GUARDIAN_API_TOKEN_FILE` configures the bearer
  token accepted by `/api/v1/*`. The file form is recommended for Docker
  secrets.
- `REQUIRE_API_AUTH=true` fails closed when no token can be loaded.
- `RATE_LIMIT_PER_MINUTE` enables a lightweight per-client/token limit; `0`
  disables it for local development.
- `MAX_REQUEST_BODY_BYTES` rejects oversized requests before JSON parsing.

`GET /health` remains public for container and uptime health checks and reports
whether authentication is required and configured. It never returns a token.

## RunPod container

From the repository root:

```bash
docker build --file deploy/runpod/Dockerfile \
  --tag <DOCKER_USER>/abap-guardian-runpod:0.5.0-runpod1 .
```

The RunPod image keeps Guardian on port 8001, ABAP Expert on private port 8000
and Ollama on private port 11434. Guardian authenticates Eclipse requests with
`GUARDIAN_API_TOKEN`; retrieval and generation remain inside the Pod.

## Quick start

```bash
pip install -e .
export ANALYZER_JAR=../analyzer-core/target/analyzer-core-0.5.0-SNAPSHOT.jar
uvicorn gateway.main:app --port 8000
```

## Guarantees

- ABAP source is never stored or logged; it is piped to the analyzer via
  stdin only.
- AI output can only refine explanation/recommendation/suggested code —
  never positions or the finding set.
- A redaction layer masks likely personal data and credentials before the
  private ABAP Expert model receives a prompt.
- Chat and finding enhancement send at most 4000 characters of redacted code
  context to the model inside the Pod.
- Production API tokens are compared in constant time and never logged.

## Tests

```bash
pip install -e ".[dev]"
pytest
```
