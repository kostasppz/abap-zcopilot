# Rules Reference

All 34 rules are deterministic and token/statement based; keywords inside
comments and string literals never trigger. Positions are 1-based lines and
columns. Configuration lives in `rules/*.yaml` (enable/disable, severity
override, confidence threshold, sensitive identifiers, approved
destinations, thresholds).

## Performance (14)

| Rule | Default severity | What it flags |
| --- | --- | --- |
| `PERF_SELECT_IN_LOOP` | HIGH | Any SELECT inside a LOOP/DO/WHILE/SELECT-loop/PROVIDE. |
| `PERF_SELECT_STAR` | MEDIUM | `SELECT *` where a field list would do. |
| `PERF_SELECT_ENDSELECT` | MEDIUM | Row-by-row SELECT…ENDSELECT loops. |
| `PERF_DATABASE_CHANGE_IN_LOOP` | HIGH | INSERT/UPDATE/MODIFY/DELETE against DB tables inside loops (internal-table operations excluded). |
| `PERF_RFC_OR_FUNCTION_IN_LOOP` | HIGH | CALL FUNCTION (esp. with DESTINATION) inside loops. |
| `PERF_NESTED_STANDARD_TABLE_LOOP` | MEDIUM | Nested LOOPs without keys/secondary keys. |
| `PERF_REPEATED_SORT_IN_LOOP` | MEDIUM | SORT inside a loop. |
| `PERF_COMMIT_IN_LOOP` | HIGH | COMMIT WORK / ROLLBACK inside a loop. |
| `PERF_REPEATED_READ_TABLE` | MEDIUM | READ TABLE … WITH KEY in a loop without BINARY SEARCH / sorted key. |
| `PERF_UNBOUNDED_INTERNAL_TABLE` | MEDIUM | SELECT INTO TABLE without WHERE / UP TO n ROWS / FOR ALL ENTRIES. |
| `PERF_DYNAMIC_SQL` | MEDIUM | Dynamic FROM/WHERE clauses (optimizer-hostile). |
| `PERF_UNUSED_SELECTED_FIELDS` | LOW | Selected fields never referenced afterwards (heuristic). |
| `PERF_SELECT_WITHOUT_WHERE` | MEDIUM | SELECT without any row restriction. |
| `PERF_FOR_ALL_ENTRIES_WITHOUT_EMPTY_CHECK` | HIGH | FOR ALL ENTRIES without a prior emptiness check — empty driver table selects **everything**. |

## Security (11)

| Rule | Default severity | What it flags |
| --- | --- | --- |
| `SEC_HARDCODED_PASSWORD` | CRITICAL | Password-like variables assigned non-empty literals. |
| `SEC_HARDCODED_TOKEN` | CRITICAL | API keys / bearer tokens in literals. |
| `SEC_DYNAMIC_SQL_INPUT` | HIGH | Dynamic SQL clauses (injection risk). |
| `SEC_UNSAFE_CALL_TRANSACTION` | HIGH | CALL TRANSACTION without `WITH AUTHORITY-CHECK`; explicit `WITHOUT AUTHORITY-CHECK` is CRITICAL. |
| `SEC_DYNAMIC_ABAP_GENERATION` | CRITICAL | GENERATE SUBROUTINE POOL / INSERT REPORT. |
| `SEC_OS_COMMAND` | CRITICAL | `CALL 'SYSTEM'`, SXPG command execution. |
| `SEC_UNSAFE_DATASET_PATH` | HIGH | OPEN DATASET with variable paths (traversal risk). |
| `SEC_CLIENT_SPECIFIED` | MEDIUM | CLIENT SPECIFIED cross-client access. |
| `SEC_MISSING_SY_SUBRC_HANDLING` | HIGH | AUTHORITY-CHECK whose sy-subrc is never evaluated. |
| `SEC_INSECURE_HTTP` | MEDIUM | Plain `http://` endpoints in literals. |
| `SEC_AUTHORIZATION_CHECK_INDICATOR` | INFO | **Heuristic** indicator that a human should verify authorization handling. Never asserts a check is missing; confidence 0.3; always requires human review. |

## Privacy (9)

Sensitive identifiers/tables are configurable (`rules/privacy.yaml`); their
presence alone is never a violation — an output/exfiltration context is
required. All privacy findings require human review.

| Rule | Default severity | Context |
| --- | --- | --- |
| `PRIV_PERSONAL_DATA_IN_LOG` | HIGH | Sensitive identifiers in BAL_LOG calls / LOG-POINT. |
| `PRIV_PERSONAL_DATA_IN_MESSAGE` | MEDIUM | Sensitive identifiers in MESSAGE. |
| `PRIV_PERSONAL_DATA_IN_SPOOL` | HIGH | Sensitive identifiers in WRITE / list output. |
| `PRIV_PERSONAL_DATA_IN_FILE_EXPORT` | HIGH | TRANSFER / GUI_DOWNLOAD of sensitive data. |
| `PRIV_BROAD_HR_MASTER_DATA_SELECTION` | HIGH | `SELECT *` or unrestricted reads from sensitive tables. |
| `PRIV_UNMASKED_PERSONNEL_NUMBER` | MEDIUM | PERNR output without masking. |
| `PRIV_EXTERNAL_DATA_TRANSFER` | HIGH | RFC/HTTP to destinations not in `approvedDestinations` while personal data is in scope. |
| `PRIV_EXCESSIVE_FIELD_SELECTION` | MEDIUM | More sensitive fields selected than `excessiveFieldThreshold`. |
| `PRIV_DEBUG_OUTPUT_OF_PERSONAL_DATA` | MEDIUM | cl_demo_output / BREAK-POINT near personal data. |

## Suppressions

```abap
<statement>. "#EC ABAP_GUARDIAN: RULE_ID reason="documented justification"
```

- Applies to findings on the same line (±1).
- The `reason` attribute is **mandatory**; without it the suppression is
  invalid and the finding remains.
- Suppressed findings are still reported in `suppressedFindings` for audit.

## Configuration keys

```yaml
defaultConfidenceThreshold: 0.0   # drop findings below this confidence
excessiveFieldThreshold: 3
sensitiveTables: [PA0002, PA0006, PA0009]
sensitiveFields: [PERNR, NACHN, VORNA, GBDAT, STRAS, ORT01, BANKN, IBAN, USRID, EMAIL, PHONE]
approvedDestinations: []
rules:
  RULE_ID:
    enabled: true
    severity: HIGH            # override default
    confidenceThreshold: 0.5  # per-rule threshold
```
