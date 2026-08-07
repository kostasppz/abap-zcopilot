# ABAP Guardian

AI-assisted static analysis and code review for SAP ABAP — deterministic
rules first, local AI second, your code never leaves your machine by default.

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

## What it does

ABAP Guardian analyzes ABAP source code for **performance**, **security**
and **privacy** problems using 34 deterministic, tokenizer/statement-model
based rules with accurate line and column positions. An optional AI gateway
(FastAPI + [Ollama](https://ollama.com), default model `gemma4:e4b`) enriches
findings with better explanations and suggested fixes — fully locally.

An Eclipse plug-in integrates the analysis into ABAP Development Tools:
findings view, editor annotations, compare-based fix preview with explicit
confirmation and single undo.

> **Important non-guarantee disclaimer:** ABAP Guardian is an assistance
> tool. Its findings are heuristics with confidence values; it does not
> guarantee the absence of performance problems, security vulnerabilities or
> privacy violations, and it never asserts that authorization checks are
> missing — it only indicates where a human should verify them. All
> suggested fixes require human review before use. Use of the results is at
> your own responsibility.

## Repository layout

| Path | Description |
| --- | --- |
| `analyzer-core/` | Pure Java 21 rule engine (no Eclipse dependencies) with CLI. |
| `ai-gateway/` | Python FastAPI gateway; deterministic-first, optional local AI. |
| `eclipse-plugin/` | Eclipse/ADT plug-in (public APIs only). |
| `eclipse-feature/`, `eclipse-updatesite/` | Feature + p2 update site (Tycho). |
| `rules/` | Default YAML rule configuration (performance, security, privacy, policy). |
| `samples/` | Good and bad ABAP examples. |
| `docs/` | Architecture, rule docs, privacy/security model, guides. |

## Quick start

### 1. Build the analyzer

```bash
mvn clean verify        # builds and tests analyzer-core (default profile)
```

### 2. Run the AI gateway

```bash
cd ai-gateway
pip install -e .
export ANALYZER_JAR=../analyzer-core/target/analyzer-core-0.1.0-SNAPSHOT.jar
uvicorn gateway.main:app --port 8000
```

Optional local AI: install Ollama and pull the default model
(`ollama pull gemma4:e4b`). Override via `OLLAMA_BASE_URL` / `OLLAMA_MODEL`.
Without Ollama the gateway still works — deterministic findings only.

### 3. Analyze something

```bash
curl -s localhost:8000/api/v1/analyze \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --rawfile src samples/bad/z_bad_hr_report.abap '{source: $src, objectName: "Z_BAD_HR_REPORT"}')"
```

Or without the gateway:

```bash
java -jar analyzer-core/target/analyzer-core-0.1.0-SNAPSHOT.jar samples/bad/z_bad_hr_report.abap
```

### 4. Eclipse plug-in

```bash
mvn clean verify -Peclipse   # needs network access to download.eclipse.org
```

The p2 update-site ZIP lands in `eclipse-updatesite/target/`. Install via
*Help → Install New Software… → Add → Archive*. See
`docs/eclipse-development.md`.

With an ABAP source editor active, press `Ctrl+Alt+G` on Windows/Linux or
`Cmd+Option+G` on macOS to run **Analyze Current Editor**. The shortcut can be
changed in *Window → Preferences → General → Keys*.

## Rules

34 rules across three categories — 14 `PERF_*`, 11 `SEC_*`, 9 `PRIV_*` — all
documented in [`docs/rules.md`](docs/rules.md) and configurable via YAML in
[`rules/`](rules/). Highlights:

- Token/statement based analysis: keywords inside comments and string
  literals never cause false positives.
- `SEC_AUTHORIZATION_CHECK_INDICATOR` is explicitly heuristic and never
  claims a check is missing.
- Privacy rules are context-aware: sensitive identifiers (configurable) are
  flagged only when they reach logs, messages, spool, files or external
  destinations.

### Suppressions

```abap
WRITE lv_pernr. "#EC ABAP_GUARDIAN: PRIV_UNMASKED_PERSONNEL_NUMBER reason="Approved audit list DP-142"
```

The `reason` is **mandatory** — suppressions without one are ignored.

## Privacy & security posture

- **Local by default.** Analysis and AI run on your machine; external AI
  providers are disabled unless explicitly enabled, and a redaction layer
  masks likely personal data and credentials in prompts.
- **No source retention.** The gateway never stores or logs ABAP source
  (tests enforce this); source is piped to the analyzer via stdin only.
- **AI cannot lie about positions.** Line/column numbers come exclusively
  from the deterministic engine; AI output is schema-validated and can only
  refine explanation text.

Details: [`docs/privacy-model.md`](docs/privacy-model.md),
[`docs/security-model.md`](docs/security-model.md).

## Documentation

- [Architecture](docs/architecture.md)
- [Project specification](docs/project-specification.md)
- [Rules reference](docs/rules.md)
- [Local Ollama setup](docs/local-ollama.md)
- [Eclipse development](docs/eclipse-development.md)
- [Releasing](docs/releasing.md)
- [Troubleshooting](docs/troubleshooting.md)
- [ATC roadmap](docs/atc-roadmap.md) (future SAP-side checks — not a dependency)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security reports: see
[SECURITY.md](SECURITY.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
