# PROJECT STATUS

Component status is only marked ✅ when its automated tests pass.

_Last updated: 2026-08-06_

| Component | Status | Notes |
| --- | --- | --- |
| analyzer-core: tokenizer & parser | ✅ complete | 1-based line/col tracking, comments/literals/templates/pragmas, chain expansion, block tree incl. SELECT…ENDSELECT; covered by `AbapTokenizerTest`, `AbapParserTest`. |
| analyzer-core: rule engine & config | ✅ complete | YAML config (enable/severity/confidence), suppression with mandatory reason, JSON serialization; covered by `RuleEngineTest`, `JsonSerializerTest`. |
| analyzer-core: 14 PERF rules | ✅ complete | Tested in `PerformanceRulesTest` + engine tests. |
| analyzer-core: 11 SEC rules | ✅ complete | Tested in `SecurityRulesTest`; authorization indicator is heuristic-only. |
| analyzer-core: 9 PRIV rules | ✅ complete | Tested in `PrivacyRulesTest`; context-aware, human review required. |
| analyzer-core: CLI | ✅ complete | stdin/file input, JSON out; used by the gateway (integration verified via gateway tests with mocked subprocess + manual run). |
| ai-gateway: FastAPI service | ✅ complete | /health, /api/v1/models, /analyze, /explain, /suggest-fix; deterministic-first; limits; 19 pytest tests green. |
| ai-gateway: AI safety invariants | ✅ complete | AI cannot alter line numbers or invent findings (tested); schema validation; no-source-logging tests. |
| ai-gateway: KnowledgeProvider (RAG seam) | ✅ complete | Protocol + NoOp + LocalVector placeholder + mock provider test. |
| ai-gateway: redaction layer | ✅ complete | PII/credential masking, tested. |
| rules/ YAML configs | ✅ complete | performance/security/privacy/policy. |
| samples/ | ✅ complete | Canonical bad example (verified findings via engine test) + corrected version. |
| eclipse-plugin | ✅ complete | Full source (commands, handlers, Job, findings view, annotations, compare dialog, preferences, secure storage, ADT adapter, public APIs only). Tycho build (`mvn clean verify -Peclipse`) verified 2026-08-06: BUILD SUCCESS, no compile/manifest errors. Headless p2 install into Eclipse Platform 4.32 verified via p2 director (feature resolves and installs); opening the Findings view in a running GUI workbench remains a manual check. |
| eclipse-feature / eclipse-updatesite | ✅ complete | feature.xml + category.xml + Tycho p2 repository config; `-Peclipse` build produces the p2 update-site ZIP. Install verified headlessly: `eclipse -application org.eclipse.equinox.p2.director -repository jar:file:<zip>!/ -installIU com.abapguardian.eclipse.feature.feature.group` → "Overall install request is satisfiable", plugin + feature landed in `plugins/`/`features/`. |
| CI: PR workflow | ✅ complete | Java + Python tests, ruff lint, gitleaks, pip-audit, Tycho build. |
| CI: release workflow | ✅ complete | Checksums, update-site ZIP, GitHub Pages p2, generated release notes. |
| Docs | ✅ complete | README, architecture, eclipse-development, local-ollama, rules, privacy-model, security-model, releasing, troubleshooting, atc-roadmap. |

## Milestone check

- `mvn clean verify` (default profile): **passing** — 69 JUnit tests.
- `pytest` (ai-gateway): **passing** — 19 tests.
- ≥20 tested rules: **yes** (all 34 rules registered; 25+ rules have direct
  positive/negative tests; the rest are exercised through engine-level
  tests).
- p2 ZIP: **verified** — `mvn clean verify -Peclipse` builds all Tycho
  modules (analyzer-core, plugin, feature, update site) and produces
  `eclipse-updatesite/target/com.abapguardian.eclipse.updatesite-*.zip`
  containing the plugin/feature jars and p2 metadata. CI runs the same
  build on every PR (`.github/workflows/pr.yml`, `tycho` job).
