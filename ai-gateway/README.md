# ABAP Guardian AI Gateway

FastAPI service that runs the deterministic ABAP Guardian analyzer first and
optionally enriches findings through hosted OpenAI Responses, direct local
Ollama, or the local ABAP Expert streaming RAG service. See the repository
root `README.md` and `docs/` for full documentation.

## Local ABAP Expert RAG provider

```text
LLM_PROVIDER=abap-agent
ABAP_AGENT_BASE_URL=http://abap-ai:8000
ABAP_AGENT_MODEL=abap-expert
ALLOW_EXTERNAL_PROVIDERS=false
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

## Hosted container

From the repository root:

```bash
docker build -t abap-guardian .
docker run --rm -p 8000:8000 \
  -e LLM_PROVIDER=openai \
  -e ALLOW_EXTERNAL_PROVIDERS=true \
  -e OPENAI_API_KEY \
  abap-guardian
```

The API key remains on the server. OpenAI requests set `store: false` and
receive only the bounded prompt after Guardian redaction.

The container also bundles curated Markdown/YAML from `docs/` and `rules/`.
`POST /api/v1/chat` performs dependency-free lexical retrieval over those
files and includes only the most relevant bounded passages in the prompt.
Knowledge retrieval happens inside the Guardian container; it does not require
or upload documentation to an external vector database.

## Quick start

```bash
pip install -e .
export ANALYZER_JAR=../analyzer-core/target/analyzer-core-0.4.0-SNAPSHOT.jar
uvicorn gateway.main:app --port 8000
```

## Guarantees

- ABAP source is never stored or logged; it is piped to the analyzer via
  stdin only.
- AI output can only refine explanation/recommendation/suggested code —
  never positions or the finding set.
- External AI providers are disabled by default; a redaction layer masks
  likely personal data and credentials in prompts.
- Chat and finding enhancement send at most 4000 characters of redacted code
  context onward to an external provider.
- Production API tokens are compared in constant time and never logged.

## Tests

```bash
pip install -e ".[dev]"
pytest
```
