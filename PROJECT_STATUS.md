# PROJECT STATUS

Component status is only marked ✅ when its automated tests pass.

_Last updated: 2026-08-07_

| Component | Status | Notes |
| --- | --- | --- |
| analyzer-core: tokenizer & parser | ✅ complete | 1-based line/col tracking, comments/literals/templates/pragmas, chain expansion, block tree incl. SELECT…ENDSELECT; covered by `AbapTokenizerTest`, `AbapParserTest`. |
| analyzer-core: rule engine & config | ✅ complete | YAML config (enable/severity/confidence), suppression with mandatory reason, JSON serialization; covered by `RuleEngineTest`, `JsonSerializerTest`. |
| analyzer-core: 14 PERF rules | ✅ complete | Tested in `PerformanceRulesTest` + engine tests. |
| analyzer-core: 11 SEC rules | ✅ complete | Tested in `SecurityRulesTest`; authorization indicator is heuristic-only. |
| analyzer-core: 9 PRIV rules | ✅ complete | Tested in `PrivacyRulesTest`; context-aware, human review required. |
| analyzer-core: CLI | ✅ complete | stdin/file input, JSON out; used by the gateway (integration verified via gateway tests with mocked subprocess + manual run). |
| ai-gateway: FastAPI service | ✅ complete | Hosted OpenAI Responses or local Ollama; deterministic-first; chat endpoint; limits; 36 pytest tests green. |
| ai-gateway: AI safety invariants | ✅ complete | AI cannot alter line numbers or invent findings (tested); schema validation; no-source-logging tests. |
| ai-gateway: KnowledgeProvider | ✅ complete | Protocol + bundled repository-doc/rule lexical retrieval + optional local vector provider; tested. |
| ai-gateway: redaction layer | ✅ complete | PII/credential masking, tested. |
| Hosted deployment | ✅ repository-ready | Docker packages Java 21 analyzer + Python gateway; Render requires a server-side `OPENAI_API_KEY`. Live deployment remains an operator step. |
| rules/ YAML configs | ✅ complete | performance/security/privacy/policy. |
| samples/ | ✅ complete | Canonical bad example (verified findings via engine test) + corrected version. |
| eclipse-plugin 0.3.0 | 🟡 implemented, CI pending | Copilot/Welcome views, docked perspective contribution, chat/context actions, debounced live/on-save analysis, status, editor annotations, Description/Suggestion columns and preview-only corrections. Local XML validation passes; Tycho compile and GUI smoke test remain. |
| eclipse-feature / eclipse-updatesite | ✅ complete | feature.xml + category.xml + Tycho p2 repository config; `-Peclipse` build produces the p2 update-site ZIP. Install verified headlessly: `eclipse -application org.eclipse.equinox.p2.director -repository jar:file:<zip>!/ -installIU com.abapguardian.eclipse.feature.feature.group` → "Overall install request is satisfiable", plugin + feature landed in `plugins/`/`features/`. |
| CI: PR workflow | ✅ complete | Java + Python tests, ruff lint, gitleaks, pip-audit, Tycho build. |
| CI: release workflow | ✅ complete | Checksums, update-site ZIP, GitHub Pages p2, generated release notes. |
| Docs | ✅ complete | README, architecture, eclipse-development, local-ollama, rules, privacy-model, security-model, releasing, troubleshooting, atc-roadmap. |

## Milestone check

- `mvn clean verify` (default profile): **passing** — 69 JUnit tests.
- `pytest` (ai-gateway): **passing** — 36 tests, including hosted OpenAI mock integration, stateless chat, knowledge retrieval, no-source-logging and explicit opt-in enforcement.
- ≥20 tested rules: **yes** (all 34 rules registered; 25+ rules have direct
  positive/negative tests; the rest are exercised through engine-level
  tests).
- p2 ZIP: **verified** — `mvn clean verify -Peclipse` builds all Tycho
  modules (analyzer-core, plugin, feature, update site) and produces
  `eclipse-updatesite/target/com.abapguardian.eclipse.updatesite-*.zip`
  containing the plugin/feature jars and p2 metadata. CI runs the same
  build on every PR (`.github/workflows/pr.yml`, `tycho` job).
