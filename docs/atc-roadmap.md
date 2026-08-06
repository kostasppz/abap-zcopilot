# ATC Roadmap (future work — not a dependency)

ABAP Guardian runs entirely on the developer's machine today. A natural
future extension is SAP-side integration with the **ABAP Test Cockpit
(ATC)** so the same rule set can participate in central quality gates.
Nothing in the current architecture depends on this roadmap.

## Vision

1. **Phase 1 — exchange format (done today).** The JSON wire format already
   carries everything an ATC check would need (rule IDs, positions,
   severities, suppressions with mandatory reasons).
2. **Phase 2 — ABAP check class bridge.** A `CL_CI_TEST_SCAN`-based check
   class in a customer namespace calls the Guardian gateway (or an
   equivalent on-premise service) per object and maps findings to ATC
   messages. Pseudo-comment suppressions map 1:1 because ABAP Guardian
   already uses the `"#EC` mechanism.
3. **Phase 3 — native rule port.** The most valuable deterministic rules are
   re-implemented natively in ABAP (Code Inspector checks) so that central
   ATC runs need no external service at all. The YAML configuration would
   be mirrored into check variant attributes.
4. **Phase 4 — exemption workflow alignment.** Guardian's
   reason-mandatory suppressions align with ATC exemptions; a sync tool
   could translate one into the other for audit consistency.

## Design guardrails for the port

- Rule IDs stay stable across implementations (`PERF_*`, `SEC_*`, `PRIV_*`).
- ATC-side checks must keep the same honesty rules: heuristic indicators
  (like `SEC_AUTHORIZATION_CHECK_INDICATOR`) never assert absence of
  authorization checks.
- Privacy rules remain context-based; central runs must not turn sensitive
  identifiers into automatic violations.
- No source ever leaves the SAP system in phases 2–4; AI enhancement would
  remain a developer-side, local feature.

## Non-goals

- Replacing ATC, Code Inspector or SAP's own security tooling.
- Uploading code to any external service as part of central checks.
