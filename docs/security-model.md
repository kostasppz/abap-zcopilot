# Security Model

## Threat surface

| Component | Exposure | Mitigations |
| --- | --- | --- |
| ai-gateway | localhost HTTP | No auth by design (localhost only); configurable limits (`MAX_SOURCE_LENGTH`, timeouts, `MAX_FINDINGS`, `MAX_TOKENS`) bound resource use; no source retention. |
| analyzer-core | subprocess | Source via stdin only; JSON out; no network access. |
| Eclipse plug-in | IDE | Read-only analysis; edits only after explicit confirmation; single-undo; never saves/activates; secure storage for any credentials. |
| Ollama | localhost HTTP | Local model; schema-validated JSON responses; timeouts. |

## Gateway hardening

- **Input limits.** Requests larger than `MAX_SOURCE_LENGTH` are rejected
  (HTTP 413). Findings are capped at `MAX_FINDINGS`; generation at
  `MAX_TOKENS`; every outbound call has a timeout.
- **AI output distrust.** Model replies are untrusted input: they must parse
  as JSON and validate against Pydantic schemas. Position fields from the
  model are ignored unconditionally.
- **Error hygiene.** Analyzer stderr is never propagated verbatim; error
  messages contain no source content.
- **No secrets.** The default configuration requires no credentials at all.

## Eclipse plug-in hardening

- Public Eclipse APIs only — no `.internal` imports, reducing breakage and
  audit surface.
- Suggested fixes are shown in a compare dialog; application requires an
  explicit confirmation and results in exactly one undoable document edit.
- The plug-in never auto-saves and never triggers activation of ABAP
  objects.
- `SecureCredentialStore` wraps Eclipse secure storage (encrypted) for any
  future credentials; nothing secret goes into plain preferences.

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
