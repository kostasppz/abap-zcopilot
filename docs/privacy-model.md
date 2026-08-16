# Privacy Model

## Principles

1. **Explicit service boundary.** In hosted mode, the current ABAP document
   is sent over HTTPS to the configured Guardian service for deterministic
   analysis. Deploy the service only in an environment approved for the
   source code being analyzed.
2. **No retention.** ABAP source is never stored or logged by the gateway.
   It is piped to the analyzer via stdin (never argv, never temp files) and
   exists only in memory for the duration of a request. Automated tests
   (`ai-gateway/tests/test_no_source_logging.py`) enforce this.
3. **External providers are server-side opt-in.** `LLM_PROVIDER=openai` is
   inactive unless `ALLOW_EXTERNAL_PROVIDERS=true` and `OPENAI_API_KEY` are
   configured on the server. Responses requests use `store: false`; the
   redaction layer remains active.
4. **Redaction layer.** Before any prompt leaves the Guardian service for an
   external model,
   `gateway/redaction.py` masks likely personal data (emails, IBANs,
   8-digit personnel numbers, phone numbers) and credentials (passwords,
   bearer tokens).
5. **Local RAG option.** With `LLM_PROVIDER=abap-agent`, Guardian and the ABAP
   Expert RAG service run on the same local Docker network. No OpenAI key is
   configured and ports in the supplied Compose example bind only to
   `127.0.0.1`. The bounded redacted prompt is processed locally by Chroma and
   Ollama.
6. **Dedicated GPU boundary.** In the VM deployment, source crosses the
   network only over HTTPS to the selected VM and is processed in memory.
   Ollama and Chroma are not published, and Caddy mediates any optional ABAP
   Expert browser access with separate authentication. The API token controls
   Eclipse access but does not replace approval of the provider, region,
   administrators, backup policy and organizational retention controls.

## Privacy rules are context-aware

Sensitive identifiers — by default `PERNR, NACHN, VORNA, GBDAT, STRAS,
ORT01, BANKN, IBAN, USRID, EMAIL, PHONE` and tables `PA0002, PA0006, PA0009`
— are configurable in `rules/privacy.yaml`. Their **presence alone is never
a violation**: the rules fire only when such identifiers reach a data sink

- application logs (`PRIV_PERSONAL_DATA_IN_LOG`),
- messages (`PRIV_PERSONAL_DATA_IN_MESSAGE`),
- list/spool output (`PRIV_PERSONAL_DATA_IN_SPOOL`),
- file exports (`PRIV_PERSONAL_DATA_IN_FILE_EXPORT`),
- non-approved external destinations (`PRIV_EXTERNAL_DATA_TRANSFER`),
- debug output (`PRIV_DEBUG_OUTPUT_OF_PERSONAL_DATA`),

or when reads are broader than necessary
(`PRIV_BROAD_HR_MASTER_DATA_SELECTION`, `PRIV_EXCESSIVE_FIELD_SELECTION`,
`PRIV_UNMASKED_PERSONNEL_NUMBER`).

All privacy findings set `requiresHumanReview: true` and carry confidence
values below 1.0 — a human decides whether a real problem exists.

## What the AI can and cannot do

- The AI sees at most a bounded, redacted snippet (4000 chars) plus finding
  metadata or the current Copilot question/conversation context.
- The hosted Guardian service necessarily sees the complete document for
  deterministic analysis; the external LLM does not receive that full source.
- It can improve explanation/recommendation/suggested-code **text** only.
- It cannot add findings, remove findings, or alter line/column numbers —
  positions come exclusively from the deterministic engine and are enforced
  at merge time (`gateway/enhancement.py`), with tests proving it.

## Eclipse live analysis and chat

- Live analysis and analyze-on-save are disabled by default.
- Enabling either option means the current document is sent to the configured
  Guardian service after the chosen delay or save event.
- Automatic runs use deterministic analysis by default; automatic online-AI
  enhancement requires a separate switch.
- Copilot includes source only when **Use active ABAP editor/selection as
  context** is checked. Chat history lives in memory in the Eclipse view and is
  not persisted by the plug-in.
- OpenAI Responses requests remain stateless with `store: false`.
- Local ABAP Expert requests use its in-memory NDJSON chat endpoint. Guardian
  does not persist prompts or streamed replies.

## Suppression accountability

Suppressions require a documented reason:

```abap
"#EC ABAP_GUARDIAN: RULE_ID reason="why this is acceptable"
```

Suppressions without a reason are invalid and ignored, keeping an audit
trail for every silenced finding.
