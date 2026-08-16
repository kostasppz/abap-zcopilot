# ABAP Guardian

AI-assisted static analysis and code review for SAP ABAP — deterministic
rules first, private RunPod AI second. Eclipse users install one plug-in;
the analyzer and ABAP Expert model run in the owner's authenticated RunPod Pod.

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

## What it does

ABAP Guardian analyzes ABAP source code for **performance**, **security**,
**SAP S/4HANA compatibility**, **Clean ABAP** and **privacy** problems using
45 deterministic, tokenizer/statement-model based rules with accurate line
and column positions. Its private RunPod gateway connects to ABAP Expert,
Chroma and Ollama to enrich findings with explanations and suggested fixes.

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
| `ai-gateway/` | Python FastAPI gateway for the private ABAP Expert/Ollama service. |
| `eclipse-plugin/` | Eclipse/ADT plug-in (public APIs only). |
| `eclipse-feature/`, `eclipse-updatesite/` | Feature + p2 update site (Tycho). |
| `rules/` | YAML configuration for performance, security, S/4HANA, Clean ABAP, privacy and policy. |
| `samples/` | Good and bad ABAP examples. |
| `docs/` | Architecture, rule docs, privacy/security model, guides. |
| `Dockerfile` | Base analyzer/gateway container used by private deployments. |
| `deploy/gpu-vm/` | Authenticated HTTPS deployment for the existing Ollama/RAG stack. |
| `deploy/runpod/` | Single-container RunPod image for Guardian, ABAP Expert and Ollama. |

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

The service URL is intentionally blank after installation because every
RunPod Pod has its own ID. Enter `https://<POD_ID>-8001.proxy.runpod.net` and
store `GUARDIAN_API_TOKEN` securely in the plug-in Preferences.

## Alternative: dedicated GPU VM with the existing ABAP Expert

The production Compose deployment runs Caddy, Guardian, the existing ABAP
Expert Chroma service and Ollama on one NVIDIA GPU VM. Only HTTPS is public;
the model and RAG services stay on an isolated Docker network. Guardian API
requests require a bearer token, rate limiting is enabled, and the Eclipse
plug-in stores that token in Eclipse Secure Storage. A second HTTPS hostname
can expose the existing ABAP Expert browser chat behind separate Caddy Basic
Authentication.

Follow the full tutorial from VM sizing and DNS through model loading,
verification, Eclipse setup, updates, backups and token rotation:
[`docs/dedicated-gpu-vm.md`](docs/dedicated-gpu-vm.md).

## Recommended: RunPod deployment

RunPod avoids the AWS EC2 GPU quota workflow and runs the stack in one custom
Pod image. The agent source, knowledge, Chroma database and Ollama model data
remain on a persistent network volume. Guardian is bearer-token protected on
port 8001, while the optional Agent browser UI is exposed through a separate
password-protected Nginx proxy on port 8002. Follow the complete Docker Hub,
RunPod, security, storage and Eclipse walkthrough in
[`docs/runpod.md`](docs/runpod.md).

## Local development and self-hosting

Build and test the deterministic analyzer:

```bash
mvn clean verify
```

Run the gateway locally with Ollama:

```bash
cd ai-gateway
pip install -e .
export ANALYZER_JAR=../analyzer-core/target/analyzer-core-0.5.0-SNAPSHOT.jar
uvicorn gateway.main:app --port 8000
```

To reuse an existing local ABAP Expert agent with PDF/Word knowledge, Chroma
retrieval and Ollama, select `LLM_PROVIDER=abap-agent`. The recommended
three-container setup and Eclipse configuration are documented in
[`docs/local-abap-agent.md`](docs/local-abap-agent.md).

Build the Eclipse update site:

```bash
mvn clean verify -Peclipse   # needs network access to download.eclipse.org
```

The p2 update-site ZIP lands in `eclipse-updatesite/target/`. Install via
*Help → Install New Software… → Add → Archive*. See
`docs/eclipse-development.md`.

The shortcut can be changed in *Window → Preferences → General → Keys*.

## Rules

45 rules across five primary categories — 14 `PERF_*`, 11 `SEC_*`, 5 `S4_*`,
6 `CLEAN_*` and 9 `PRIV_*` — all
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
  authenticated Guardian API in the configured RunPod Pod. Only a bounded,
  redacted snippet (up to 4000 characters) reaches the private ABAP Expert
  model when AI enhancement or context-aware Copilot chat is enabled.
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
- [Local ABAP Expert RAG integration](docs/local-abap-agent.md)
- [Dedicated GPU VM and Eclipse tutorial](docs/dedicated-gpu-vm.md)
- [Eclipse development](docs/eclipse-development.md)
- [Releasing](docs/releasing.md)
- [Troubleshooting](docs/troubleshooting.md)
- [ATC roadmap](docs/atc-roadmap.md) (future SAP-side checks — not a dependency)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security reports: see
[SECURITY.md](SECURITY.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
