# Contributing to ABAP Guardian

Thanks for your interest! Contributions of all kinds are welcome: new rules,
bug fixes, docs, sample ABAP, and test cases.

## Getting Started

Prerequisites: JDK 21, Maven 3.9+, Python 3.12+.

```bash
# Java core (default profile builds analyzer-core only)
mvn clean verify

# Python gateway
cd ai-gateway
pip install -e ".[dev]"
pytest

# Eclipse plug-in + p2 update site (needs network access to the Eclipse repo)
mvn clean verify -Peclipse
```

## Project Layout

| Module | Purpose |
| --- | --- |
| `analyzer-core` | Pure Java rule engine (tokenizer, parser, rules). No Eclipse dependencies. |
| `ai-gateway` | FastAPI service; deterministic analysis first, optional local AI. |
| `eclipse-plugin` / `eclipse-feature` / `eclipse-updatesite` | Eclipse/ADT integration (Tycho). |
| `rules/` | Default YAML rule configuration. |
| `samples/` | Good and bad ABAP examples used in docs and tests. |

## Adding a Rule

1. Implement the rule as a nested class in the matching category file
   (`PerformanceRules`, `SecurityRules`, `PrivacyRules`) and register it in
   the category's `all()` list.
2. Every finding must set all fields: ruleId, category, severity, confidence,
   title, explanation, evidence, positions (1-based), recommendation and
   `requiresHumanReview` where appropriate.
3. Add the rule to the corresponding YAML file in `rules/`.
4. Write tests: a positive case, a negative case, and a false-positive guard
   (keywords inside comments/string literals must not trigger).
5. Document the rule in `docs/rules.md`.

Rule philosophy:
- Deterministic, token/statement based — never plain substring matching on
  raw source.
- Honest confidence values; heuristics must say they are heuristics.
- Privacy rules never treat a sensitive identifier alone as a violation —
  context (output/exfiltration) is required.

## Code Style

- Java: standard Maven/Checkstyle-ish conventions, 4-space indent, javadoc on
  public API. No Eclipse `.internal` imports in the plug-in.
- Python: `ruff` clean, type hints required.
- Keep the gateway free of any code path that stores or logs ABAP source.

## Pull Requests

- One logical change per PR.
- `mvn clean verify` and `pytest` must pass; CI runs both plus lint,
  secret scanning and the Tycho build.
- Update `PROJECT_STATUS.md` if you change component status.

## Suppressions in Test/Sample ABAP

Use the documented pseudo-comment format — a reason is mandatory:

```abap
WRITE lv_pernr. "#EC ABAP_GUARDIAN: PRIV_UNMASKED_PERSONNEL_NUMBER reason="Approved audit list"
```
