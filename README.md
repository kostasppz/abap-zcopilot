# ABAP Guardian

AI-assisted static analysis and code review for SAP ABAP — deterministic
rules first, optional hosted AI second. Eclipse users install one plug-in;
the analyzer and model integration run on a managed HTTPS service.

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

## What it does

ABAP Guardian analyzes ABAP source code for **performance**, **security**
and **privacy** problems using 34 deterministic, tokenizer/statement-model
based rules with accurate line and column positions. An optional AI gateway
(FastAPI plus a hosted OpenAI Responses API model or local Ollama) enriches
findings with better explanations and suggested fixes.

An Eclipse plug-in integrates the analysis into ABAP Development Tools with
a docked Copilot chat, live/on-save findings, editor annotations, contextual
quick actions and compare-based fix previews with explicit confirmation.

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
| `ai-gateway/` | Python FastAPI gateway; hosted OpenAI or optional local Ollama. |
| `eclipse-plugin/` | Eclipse/ADT plug-in (public APIs only). |
| `eclipse-feature/`, `eclipse-updatesite/` | Feature + p2 update site (Tycho). |
| `rules/` | Default YAML rule configuration (performance, security, privacy, policy). |
| `samples/` | Good and bad ABAP examples. |
| `docs/` | Architecture, rule docs, privacy/security model, guides. |
| `Dockerfile`, `render.yaml` | One-container hosted deployment configuration. |

## Eclipse user: install and analyze

No Python, Java, Maven, Ollama or model installation is required on the
developer workstation.

1. In Eclipse/ADT, open *Help → Install New Software…*.
2. Use `https://kostasppz.github.io/abap-zcopilot/` in **Work with**.
3. Install **ABAP Guardian** and restart Eclipse.
4. Open an ABAP source editor and press `Ctrl+Alt+G` on Windows/Linux or
   `Cmd+Option+G` on macOS.
5. Findings open in the **Guardian Findings** view.

The **ABAP Guardian Copilot** view is stacked beside Eclipse's **Problems**
view. Open it with `Ctrl+Alt+C`, *ABAP Guardian → Open Copilot*, or
*Window → Show View → Other… → ABAP Guardian*. It can answer questions about
the active ABAP object or selected text and cites bundled repository knowledge.

The Findings table keeps Severity, Category, Rule, Line, Confidence and Title,
and also shows **Description** and **Suggestion**. Right-click a finding and
choose **Review Suggested Fix…** for a side-by-side diff. Guardian never saves
or activates the object, and applies a proposed edit only after confirmation.
For a selected-code Copilot correction, use **Suggest correction**, then
**Review last suggestion…**; only a fenced ABAP code block can enter the diff
workflow, and the original selection must still match.

### Optional live analysis

Live analysis and analyze-on-save are disabled by default because the active
document is sent to the configured Guardian service. Enable either under
*Window → Preferences → ABAP Guardian*. The typing delay is configurable
(default five seconds), pending jobs are cancelled when typing continues, and
online AI for automatic runs has a separate opt-in. Manual analysis and chat
remain available regardless of these switches.

After installation and after every plug-in update, a Welcome/What's New view
opens once with privacy information and shortcuts to Copilot and Settings.

The plug-in is configured for `https://abap-zcopilot.onrender.com`. A project
owner must deploy that service once before the install-only flow works.

## Project owner: deploy the hosted service once

The included Dockerfile builds the Java analyzer and Python gateway into one
container. `render.yaml` configures a proof-of-concept Render deployment.

[![Deploy to Render](https://render.com/images/deploy-to-render-button.svg)](https://render.com/deploy?repo=https://github.com/kostasppz/abap-zcopilot)

1. Deploy the repository using the button above.
2. Set the secret `OPENAI_API_KEY` in the service dashboard. Never commit it.
3. Confirm `https://<service-host>/health` reports `status: "ok"`,
   `analyzerAvailable: true` and `llmAvailable: true`.
4. If Render assigns a different hostname, change
   `GuardianPreferences.DEFAULT_SERVICE_URL`, publish a new plug-in release,
   and let Eclipse users update once.

The hosted integration uses the OpenAI Responses API with `store: false`,
server-side credentials and the existing redaction layer. Chat retrieves
relevant passages from the `docs/` and `rules/` content bundled in the Docker
image; no external vector database is required. Override
`OPENAI_MODEL` in the hosting environment when required.

> **Proof-of-concept warning:** the public endpoint has no end-user
> authentication and uses the project owner's model quota. Use it only for
> controlled testing. Before production use, add organization authentication,
> rate limiting, audit controls and an approved private deployment.

## Local development and self-hosting

Build and test the deterministic analyzer:

```bash
mvn clean verify
```

Run the gateway locally with Ollama:

```bash
cd ai-gateway
pip install -e .
export ANALYZER_JAR=../analyzer-core/target/analyzer-core-0.3.0-SNAPSHOT.jar
uvicorn gateway.main:app --port 8000
```

Build the Eclipse update site:

```bash
mvn clean verify -Peclipse   # needs network access to download.eclipse.org
```

The p2 update-site ZIP lands in `eclipse-updatesite/target/`. Install via
*Help → Install New Software… → Add → Archive*. See
`docs/eclipse-development.md`.

The shortcut can be changed in *Window → Preferences → General → Keys*.

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

- **Explicit hosting boundary.** The ABAP document is sent over HTTPS to the
  configured Guardian service for deterministic analysis. Only a bounded,
  redacted snippet (up to 4000 characters) is sent onward when hosted AI
  enhancement or context-aware Copilot chat is enabled.
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
