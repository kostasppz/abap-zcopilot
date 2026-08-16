# Security Policy

## Supported Versions

Only the latest released version receives security fixes.

## Reporting a Vulnerability

Please report vulnerabilities privately via GitHub Security Advisories
("Report a vulnerability" on the repository's Security tab). Do **not** open
public issues for security problems.

You can expect an acknowledgement within 7 days. Please include a minimal
reproduction and the affected component (analyzer-core, ai-gateway,
eclipse-plugin).

## Security Model (summary)

- The AI gateway is local-first: it binds to localhost, talks to a local
  Ollama instance by default, and external AI providers are **disabled by
  default**. See `docs/security-model.md` and `docs/privacy-model.md`.
- ABAP source code is never stored or logged by the gateway; it is piped to
  the deterministic analyzer via stdin only (never argv or temp files).
- The Eclipse plug-in never auto-saves, never activates objects, and applies
  suggested edits only after explicit confirmation, as a single undoable edit.
- The dedicated GPU deployment requires a Guardian API bearer token, mounts it
  as a Docker secret and keeps Ollama/Chroma private. Eclipse stores the token
  in Equinox Secure Storage; nothing is written to plain preferences or logs.

## Scope Notes

ABAP Guardian is a static analysis aid. Its findings — including security
findings — are heuristics and carry confidence values. It does not guarantee
the absence of vulnerabilities in analyzed code.
