# PROJECT STATUS

Component status is only marked ✅ when its automated tests pass.

_Last updated: 2026-08-16_

| Component | Status | Notes |
| --- | --- | --- |
| analyzer-core: tokenizer & parser | ✅ complete | 1-based line/col tracking, comments/literals/templates/pragmas, chain expansion, block tree incl. SELECT…ENDSELECT; covered by `AbapTokenizerTest`, `AbapParserTest`. |
| analyzer-core: rule engine & config | ✅ complete | YAML config (enable/severity/confidence), suppression with mandatory reason, JSON serialization; covered by `RuleEngineTest`, `JsonSerializerTest`. |
| analyzer-core: 14 PERF rules | ✅ complete | Tested in `PerformanceRulesTest` + engine tests. |
| analyzer-core: 11 SEC rules | ✅ complete | Tested in `SecurityRulesTest`; authorization indicator is heuristic-only. |
| analyzer-core: 5 S/4HANA rules | 🟡 implemented, CI pending | Simplified data-model access, obsolete declarations, native SQL and database hints; every finding includes migration guidance. |
| analyzer-core: 6 Clean ABAP rules | 🟡 implemented, CI pending | Modern syntax, method-oriented design, nesting and boolean clarity; every finding includes a preview-only suggestion. |
| analyzer-core: 9 PRIV rules | ✅ complete | Tested in `PrivacyRulesTest`; context-aware, human review required. |
| analyzer-core: CLI | ✅ complete | stdin/file input, JSON out; used by the gateway (integration verified via gateway tests with mocked subprocess + manual run). |
| ai-gateway: FastAPI service | ✅ complete | Private RunPod ABAP Expert/Ollama only; deterministic-first; category filtering, chat endpoint and limits; 49 pytest tests green. |
| ai-gateway: AI safety invariants | ✅ complete | AI cannot alter line numbers or invent findings (tested); schema validation; no-source-logging tests. |
| ai-gateway: KnowledgeProvider | ✅ complete | Protocol + bundled repository-doc/rule lexical retrieval + optional local vector provider; tested. |
| ai-gateway: redaction layer | ✅ complete | PII/credential masking, tested. |
| RunPod deployment | ✅ repository-ready | One private image packages Java 21 analyzer, Python gateway, ABAP Expert and Ollama; bearer-token API and Basic-auth web UI. |
| rules/ YAML configs | ✅ complete | performance, security, S/4HANA, Clean ABAP, privacy and policy. |
| samples/ | ✅ complete | Canonical bad example (verified findings via engine test) + corrected version. |
| eclipse-plugin 0.5.0 | 🟡 implemented, CI pending | RunPod service, encrypted token storage, all/category-only analysis commands, Copilot, live/on-save analysis, editor annotations, preview-only corrections and once-per-version Welcome. |
| eclipse-feature / eclipse-updatesite | ✅ complete | feature.xml + category.xml + Tycho p2 repository config; `-Peclipse` build produces the p2 update-site ZIP. Install verified headlessly: `eclipse -application org.eclipse.equinox.p2.director -repository jar:file:<zip>!/ -installIU com.abapguardian.eclipse.feature.feature.group` → "Overall install request is satisfiable", plugin + feature landed in `plugins/`/`features/`. |
| CI: PR workflow | ✅ complete | Java + Python tests, ruff lint, gitleaks, pip-audit, Tycho build. |
| CI: release workflow | ✅ complete | Checksums, update-site ZIP, GitHub Pages p2, generated release notes. |
| Docs | ✅ complete | README, architecture, eclipse-development, local-ollama, rules, privacy-model, security-model, releasing, troubleshooting, atc-roadmap. |

## Milestone check

- `mvn clean verify` (default profile): **CI pending for 0.5.0**; the coverage
  matrix contains positive and false-positive cases for every registered rule.
- `pytest` (ai-gateway): **passing** — 49 tests, including category filtering,
  stateless private-model chat, knowledge retrieval and no-source-logging.
- ≥20 tested rules: **yes** (all 45 registered rules have positive and
  comment/string false-positive coverage in `RuleCoverageMatrixTest`).
- p2 ZIP: **verified** — `mvn clean verify -Peclipse` builds all Tycho
  modules (analyzer-core, plugin, feature, update site) and produces
  `eclipse-updatesite/target/com.abapguardian.eclipse.updatesite-*.zip`
  containing the plugin/feature jars and p2 metadata. CI runs the same
  build on every PR (`.github/workflows/pr.yml`, `tycho` job).
