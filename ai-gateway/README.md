# ABAP Guardian AI Gateway

FastAPI service that runs the deterministic ABAP Guardian analyzer first and
optionally enriches findings through either hosted OpenAI Responses or local
Ollama. See the repository root `README.md` and `docs/` for full documentation.

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
export ANALYZER_JAR=../analyzer-core/target/analyzer-core-0.3.0-SNAPSHOT.jar
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

## Tests

```bash
pip install -e ".[dev]"
pytest
```
