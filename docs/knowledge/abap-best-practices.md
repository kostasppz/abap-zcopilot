# SAP ABAP Best-Practice Notes

Curated guidance used by the AI gateway's local knowledge base (RAG). Each
section is chunked into a retrievable snippet by
`ai-gateway/scripts/build_knowledge_index.py`. Keep sections short, factual,
and tied to rule IDs where possible.

## Database access in loops (PERF_SELECT_IN_LOOP, PERF_DATABASE_CHANGE_IN_LOOP, PERF_COMMIT_IN_LOOP)

Every database round trip inside a LOOP/DO/WHILE multiplies latency by the
iteration count. Preferred patterns: read required data once before the loop
with `SELECT ... FOR ALL ENTRIES` or a ranged `WHERE`, buffer results in a
sorted/hashed internal table, and use `READ TABLE ... WITH TABLE KEY` inside
the loop. For writes, collect changes in an internal table and issue a single
array `INSERT`/`UPDATE`/`MODIFY` after the loop. `COMMIT WORK` in a loop also
destroys the logical unit of work and can commit partial data.

## Wide and unbounded SELECTs (PERF_SELECT_STAR, PERF_SELECT_WITHOUT_WHERE, PERF_UNBOUNDED_INTERNAL_TABLE)

Select only the fields you use and always restrict rows (`WHERE`, `UP TO n
ROWS`). On HANA, column-store reads make `SELECT *` disproportionately
expensive. Unbounded `SELECT ... INTO TABLE` can exhaust memory on production
data volumes even when the development system looks fine.

## FOR ALL ENTRIES pitfalls (PERF_FOR_ALL_ENTRIES_WITHOUT_EMPTY_CHECK)

If the driver table of `FOR ALL ENTRIES` is empty, the database interface
drops the entire WHERE clause and selects every row of the table. Always
guard with `IF lt_driver IS NOT INITIAL.` before the SELECT. Also deduplicate
the driver table — duplicates multiply the generated OR conditions.

## Internal table performance (PERF_NESTED_STANDARD_TABLE_LOOP, PERF_REPEATED_READ_TABLE, PERF_REPEATED_SORT_IN_LOOP)

Standard-table lookups are linear scans. Use SORTED or HASHED tables (or
secondary keys) for lookups inside loops; `READ TABLE ... BINARY SEARCH`
requires a matching prior `SORT` and is error-prone — prefer table keys.
Never SORT inside a loop; sort once outside.

## Authority checks (SEC_UNSAFE_CALL_TRANSACTION, SEC_MISSING_SY_SUBRC_HANDLING)

`CALL TRANSACTION` bypasses the caller's authorization context unless `WITH
AUTHORITY-CHECK` is specified (or a check via `AUTHORITY_CHECK_TCODE` is
done). An `AUTHORITY-CHECK` statement is meaningless unless `sy-subrc` is
evaluated immediately afterwards and the failure path actually prevents the
protected action.

## Injection and dynamic code (SEC_DYNAMIC_SQL_INPUT, SEC_DYNAMIC_ABAP_GENERATION, SEC_OS_COMMAND)

Dynamic WHERE/FROM clauses built from user input enable SQL injection — use
`cl_abap_dyn_prg` escaping helpers or avoid dynamic SQL entirely.
`GENERATE SUBROUTINE POOL` / `INSERT REPORT` create executable code at runtime
and are almost never acceptable in application code. OS command execution
must go through SM69-maintained logical commands, never raw `CALL 'SYSTEM'`.

## Secrets in code (SEC_HARDCODED_PASSWORD, SEC_HARDCODED_TOKEN)

Credentials in source end up in transports, version history, and code
reviews. Store secrets in secure storage (SSF, secure store, or destination
service credentials in RFC destinations) and reference them at runtime.

## Handling personal data (PRIV_PERSONAL_DATA_IN_LOG, PRIV_PERSONAL_DATA_IN_SPOOL, PRIV_PERSONAL_DATA_IN_FILE_EXPORT)

Personal identifiers (PERNR, names, birth dates, bank data) must not be
written to application logs, spool lists, or file exports without a
documented purpose and masking where possible. Logs and spools are broadly
readable and retained long-term; they routinely fail GDPR data-minimization
review. Mask identifiers (e.g. show only the last digits of PERNR) and
restrict exports to approved, access-controlled destinations
(PRIV_EXTERNAL_DATA_TRANSFER).

## Cross-client access (SEC_CLIENT_SPECIFIED)

`CLIENT SPECIFIED` reads bypass the automatic client isolation that most
authorization concepts silently rely on. It is legitimate only in system
programs (client copy, monitoring); in application code it usually indicates
a design problem and a data-protection risk.
