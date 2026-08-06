# Privacy Model

## Principles

1. **Local by default.** Deterministic analysis and AI enhancement run on
   the developer's machine. The gateway binds to localhost and talks to a
   local Ollama instance.
2. **No retention.** ABAP source is never stored or logged by the gateway.
   It is piped to the analyzer via stdin (never argv, never temp files) and
   exists only in memory for the duration of a request. Automated tests
   (`ai-gateway/tests/test_no_source_logging.py`) enforce this.
3. **External providers are opt-in.** `ALLOW_EXTERNAL_PROVIDERS` defaults to
   false. Enabling it is an explicit, informed decision; the redaction layer
   remains active regardless.
4. **Redaction layer.** Before any prompt leaves the process boundary,
   `gateway/redaction.py` masks likely personal data (emails, IBANs,
   8-digit personnel numbers, phone numbers) and credentials (passwords,
   bearer tokens).

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

- The AI sees at most a bounded snippet (4000 chars) plus finding metadata.
- It can improve explanation/recommendation/suggested-code **text** only.
- It cannot add findings, remove findings, or alter line/column numbers —
  positions come exclusively from the deterministic engine and are enforced
  at merge time (`gateway/enhancement.py`), with tests proving it.

## Suppression accountability

Suppressions require a documented reason:

```abap
"#EC ABAP_GUARDIAN: RULE_ID reason="why this is acceptable"
```

Suppressions without a reason are invalid and ignored, keeping an audit
trail for every silenced finding.
