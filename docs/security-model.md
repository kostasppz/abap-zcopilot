# Security Model

## Threat surface

| Component | Exposure | Mitigations |
| --- | --- | --- |
| ai-gateway | Public HTTPS | No source retention; bearer-token authentication, request/rate limits and configurable analysis limits. |
| analyzer-core | subprocess | Source via stdin only; JSON out; no network access. |
| Eclipse plug-in | IDE | Read-only analysis; edits only after explicit confirmation; single-undo; never saves/activates; secure storage for any credentials. |
| Hosted LLM | Outbound HTTPS | Server-side API key, explicit opt-in, redacted bounded prompts, `store: false`, schema validation and timeouts. |
| Ollama (optional) | localhost HTTP | Local development model; schema-validated JSON responses; timeouts. |
| ABAP Expert RAG (optional) | Local Docker network | Loopback-only published ports, Chroma/PDF/Word retrieval, bounded prompts, NDJSON parsing, sanitized errors and timeouts. |
| Dedicated GPU VM | Caddy on 80/443 only | Automatic TLS; Guardian API bearer token; separate Basic Auth for the optional browser chat; Ollama/Chroma remain private. |
| RunPod GPU Pod | HTTPS proxy on 8001/8002 | Guardian bearer token on 8001; separate Nginx Basic Auth on 8002; Agent and Ollama remain loopback-only; secrets injected from RunPod Secrets. |

## Gateway hardening

- **Input limits.** Requests larger than `MAX_SOURCE_LENGTH` are rejected
  (HTTP 413). Findings are capped at `MAX_FINDINGS`; generation at
  `MAX_TOKENS`; every outbound call has a timeout.
- **AI output distrust.** Model replies are untrusted input: they must parse
  as JSON and validate against Pydantic schemas. Position fields from the
  model are ignored unconditionally.
- **Error hygiene.** Analyzer stderr is never propagated verbatim; error
  messages contain no source content.
- **Server-side secrets.** `OPENAI_API_KEY` is supplied by the hosting secret
  store and is never committed or distributed in the Eclipse plug-in.
- **Client authentication.** Production Compose mounts
  `GUARDIAN_API_TOKEN_FILE` as a Docker secret. Every `/api/v1/*` request uses
  a constant-time bearer-token check; authentication fails closed when the
  required secret cannot be loaded.
- **Abuse limits.** The gateway rejects oversized requests before parsing and
  can enforce a bounded per-client/token request rate. Organization-scale
  deployments should add centralized identity, audit and monitoring controls.

The included public Render configuration is for a controlled proof of
concept. It deliberately supports install-only clients but therefore spends
the service owner's model quota. Production deployment requires identity,
authorization, per-user quotas, abuse protection and organizational approval.

## Eclipse plug-in hardening

- Public Eclipse APIs only — no `.internal` imports, reducing breakage and
  audit surface.
- Suggested fixes are shown in a compare dialog; application requires an
  explicit confirmation and results in exactly one undoable document edit.
- The plug-in never auto-saves and never triggers activation of ABAP
  objects.
- Live analysis and analyze-on-save are off by default. Automatic online AI
  has a separate opt-in, and stale debounced jobs cannot update the editor.
- Copilot history remains in memory only. The context checkbox controls
  whether active source/selection is included in a request.
- `SecureCredentialStore` stores the Guardian API token in Eclipse Equinox
  Secure Storage; nothing secret goes into plain preferences.

## Honest security findings

Security rules report what they can actually prove from the token stream:

- `SEC_UNSAFE_CALL_TRANSACTION` distinguishes an explicit
  `WITHOUT AUTHORITY-CHECK` (critical) from a merely absent addition (high).
- `SEC_AUTHORIZATION_CHECK_INDICATOR` is **explicitly heuristic**: it never
  asserts a check is missing, carries low confidence (0.3), and always
  requires human review.
- `SEC_MISSING_SY_SUBRC_HANDLING` only fires when no `sy-subrc` evaluation
  follows within the next few statements.

## CI security checks

The PR workflow runs secret scanning (gitleaks) and a Python dependency
audit alongside build and tests. No secrets are committed to the repository;
release workflows use only the ephemeral `GITHUB_TOKEN`.

## Reporting

See [SECURITY.md](../SECURITY.md).
