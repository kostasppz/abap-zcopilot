# ABAP Guardian AI Gateway

FastAPI service that runs the deterministic ABAP Guardian analyzer first and
optionally enriches findings with a local Ollama model (`gemma4:e4b` by
default). See the repository root `README.md` and `docs/` for full
documentation.

## Quick start

```bash
pip install -e .
export ANALYZER_JAR=../analyzer-core/target/analyzer-core-0.1.0-SNAPSHOT.jar
uvicorn gateway.main:app --port 8000
```

## Guarantees

- ABAP source is never stored or logged; it is piped to the analyzer via
  stdin only.
- AI output can only refine explanation/recommendation/suggested code —
  never positions or the finding set.
- External AI providers are disabled by default; a redaction layer masks
  likely personal data and credentials in prompts.

## Tests

```bash
pip install -e ".[dev]"
pytest
```
