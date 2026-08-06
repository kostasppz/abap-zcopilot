Build a production-quality open-source GitHub repository named **ABAP Guardian**.

ABAP Guardian is an AI-assisted static analysis and code-review tool for SAP ABAP development. It must analyze ABAP source code for performance problems, security risks, potential privacy-policy violations, and company-specific coding-policy violations. It must explain each finding and suggest safer or faster ABAP code.

The initial product is an Eclipse plug-in for SAP ABAP Development Tools. It is similar in workflow to ABAP cleaner, but it must not copy ABAP cleaner source code. Use its general multi-module Eclipse plug-in architecture only as a conceptual reference.

## Fundamental architecture

Create a Maven multi-module repository with these modules:

```text
abap-guardian/
├── analyzer-core/
├── eclipse-plugin/
├── eclipse-feature/
├── eclipse-updatesite/
├── ai-gateway/
├── rules/
│   ├── performance/
│   ├── security/
│   ├── privacy/
│   └── policy/
├── samples/
│   ├── good/
│   └── bad/
├── tests/
├── docs/
├── .github/workflows/
├── pom.xml
├── LICENSE
├── SECURITY.md
├── CONTRIBUTING.md
└── README.md
```

Use:

* Java 21
* Maven
* Eclipse PDE and OSGi
* Maven Tycho for building the plug-in and p2 update site
* JUnit 5
* Python 3.12
* FastAPI
* Pydantic
* Ollama as the default AI provider

Do not require a cloud AI provider. The default configuration must be completely local.

## analyzer-core

Implement `analyzer-core` as a pure Java library with no Eclipse dependencies.

Create:

* An ABAP tokenizer
* A lightweight statement and block model
* Accurate line and column tracking
* A configurable rule engine
* A finding model
* A suggested-fix model
* JSON serialization
* Unit tests

Do not implement the analyzer using regular expressions alone. Regular expressions may be used for detecting literals, credentials and known identifiers, but ABAP statements and nested blocks must be recognized through the tokenizer and statement model.

Create these core interfaces:

```java
public interface AbapRule {
    String getRuleId();
    RuleCategory getCategory();
    RuleSeverity getDefaultSeverity();
    List<Finding> analyze(AnalysisContext context);
}

public interface SuggestedFix {
    String getDescription();
    Optional<TextEdit> createEdit();
}
```

Create categories:

```text
PERFORMANCE
SECURITY
PRIVACY
POLICY
MAINTAINABILITY
```

Create severities:

```text
INFO
LOW
MEDIUM
HIGH
CRITICAL
```

Every finding must contain:

```text
ruleId
category
severity
confidence
title
explanation
evidence
startLine
startColumn
endLine
endColumn
recommendation
suggestedCode
requiresHumanReview
documentationReferences
```

## Initial deterministic rules

Implement and test at least these rules.

### Performance

* PERF_SELECT_IN_LOOP
* PERF_DATABASE_CHANGE_IN_LOOP
* PERF_RFC_OR_FUNCTION_IN_LOOP
* PERF_SELECT_STAR
* PERF_SELECT_WITHOUT_WHERE
* PERF_FOR_ALL_ENTRIES_WITHOUT_EMPTY_CHECK
* PERF_NESTED_STANDARD_TABLE_LOOP
* PERF_REPEATED_SORT_IN_LOOP
* PERF_COMMIT_IN_LOOP
* PERF_SELECT_ENDSELECT
* PERF_REPEATED_READ_TABLE
* PERF_UNBOUNDED_INTERNAL_TABLE
* PERF_DYNAMIC_SQL
* PERF_UNUSED_SELECTED_FIELDS

### Security

* SEC_HARDCODED_PASSWORD
* SEC_HARDCODED_TOKEN
* SEC_DYNAMIC_SQL_INPUT
* SEC_DYNAMIC_ABAP_GENERATION
* SEC_UNSAFE_CALL_TRANSACTION
* SEC_OS_COMMAND
* SEC_UNSAFE_DATASET_PATH
* SEC_UNVALIDATED_HTTP_TARGET
* SEC_AUTHORIZATION_CHECK_INDICATOR
* SEC_CLIENT_SPECIFIED
* SEC_MISSING_SY_SUBRC_HANDLING

The authorization-check rule must be clearly marked as heuristic. It must never state with certainty that an authorization check is missing.

### Privacy

* PRIV_PERSONAL_DATA_IN_LOG
* PRIV_PERSONAL_DATA_IN_MESSAGE
* PRIV_PERSONAL_DATA_IN_SPOOL
* PRIV_PERSONAL_DATA_IN_FILE_EXPORT
* PRIV_BROAD_HR_MASTER_DATA_SELECTION
* PRIV_UNMASKED_PERSONNEL_NUMBER
* PRIV_EXTERNAL_DATA_TRANSFER
* PRIV_EXCESSIVE_FIELD_SELECTION
* PRIV_DEBUG_OUTPUT_OF_PERSONAL_DATA

Support configurable sensitive identifiers such as:

```text
PERNR
NACHN
VORNA
GBDAT
STRAS
ORT01
BANKN
IBAN
USRID
EMAIL
PHONE
PA0002
PA0006
PA0009
```

These identifiers must be configurable and must not automatically mean that a violation exists. Consider statement context, data destination and confidence.

## Rule configuration

Store rules in YAML files.

Allow configuration of:

* Enabled state
* Severity override
* Confidence threshold
* Allowed exceptions
* Sensitive tables
* Sensitive fields
* Approved destinations
* Naming conventions
* Organization-specific rules
* Suppression comments

Support a suppression format such as:

```abap
"#EC ABAP_GUARDIAN: PRIV_PERSONAL_DATA_IN_LOG reason="Approved protected audit log"
```

A suppression must require a reason.

## AI gateway

Create a local FastAPI application in `ai-gateway`.

Endpoints:

```text
GET  /health
GET  /api/v1/models
POST /api/v1/analyze
POST /api/v1/explain
POST /api/v1/suggest-fix
```

The default Ollama configuration is:

```text
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=gemma4:e4b
```

Allow these environment variables to override the defaults.

The analysis request must accept:

```json
{
  "code": "...",
  "selectedStartLine": 1,
  "selectedEndLine": 50,
  "objectName": "ZCL_EXAMPLE",
  "objectType": "CLAS",
  "abapRelease": "optional",
  "includeAi": true,
  "includeSuggestedCode": true
}
```

The service must first run deterministic analysis. It may then ask Ollama to:

* Explain deterministic findings
* Rank findings
* Identify context-dependent concerns
* Suggest corrected ABAP
* Explain trade-offs

The AI must return strict JSON matching a Pydantic schema.

Do not allow the AI to invent line numbers. Line numbers must come from deterministic analysis or be validated against the supplied source.

Do not store source code, prompts or responses by default.

Do not log complete ABAP source code.

Add configurable limits:

```text
Maximum source length
Request timeout
Ollama timeout
Maximum findings
Maximum AI tokens
```

Add an optional redaction layer that replaces detected secrets and sensitive literal values before any non-local AI provider is called.

External AI providers must be disabled by default.

## Existing local RAG compatibility

Design the AI gateway so it can later connect to an existing ABAP RAG knowledge base containing:

```text
knowledge/documentation
knowledge/includes
vector_db
```

Define this interface:

```python
class KnowledgeProvider(Protocol):
    async def search(
        self,
        query: str,
        code: str,
        max_results: int
    ) -> list[KnowledgeResult]:
        ...
```

Provide:

* `NoOpKnowledgeProvider`
* `LocalVectorKnowledgeProvider` placeholder
* Tests with a mock knowledge provider

Do not attempt to embed all documentation into an Ollama Modelfile.

## Eclipse plug-in

Build an Eclipse OSGi plug-in.

Use public Eclipse APIs wherever possible:

```text
IEditorPart
ITextEditor
IDocument
ITextSelection
IAnnotationModel
IWorkbenchPage
Job
IProgressMonitor
```

Do not import packages whose names contain `.internal`.

Isolate all SAP ADT-specific functionality in an adapter package.

Add commands:

```text
Analyze Selected ABAP Code
Analyze Current ABAP Editor
Open ABAP Guardian Findings
Configure ABAP Guardian
```

Add a configurable keyboard shortcut for analysis.

The plug-in must:

1. Read the current ABAP editor selection.
2. Use the entire document when no text is selected.
3. Send the code to the configured local analysis endpoint.
4. Run network operations in a background Eclipse Job.
5. Support cancellation.
6. Never freeze the Eclipse UI.
7. Parse the JSON response safely.
8. Display findings in an “ABAP Guardian Findings” view.
9. Add inline annotations to affected lines.
10. Navigate to a finding when it is selected.
11. Show severity, category, rule ID, confidence and explanation.
12. Show original and suggested code in a comparison dialog.
13. Require explicit confirmation before modifying source code.
14. Group multiple edits into one undo operation.
15. Never activate or save the ABAP object automatically.
16. Handle connection errors without losing editor content.

Create an Eclipse preference page containing:

```text
Analysis service URL
Ollama model
Local-only mode
Enable AI explanations
Enable AI code suggestions
Minimum severity
Minimum confidence
Request timeout
Maximum source length
Rule profile
```

Default service URL:

```text
http://localhost:8000
```

Display a visible warning before enabling an external analysis service.

Store no API key directly in source code or normal preference files. Create an abstraction for Eclipse secure storage.

## User interface

Create a clean native Eclipse SWT/JFace interface.

The findings view must contain columns:

```text
Severity
Category
Rule
Line
Confidence
Title
```

The finding details panel must display:

```text
Description
Evidence
Why it matters
Suggested improvement
Original code
Suggested code
Documentation references
Human-review warning
```

Use icons for:

```text
Information
Performance
Security
Privacy
Policy
Critical warning
```

Include dark-theme-compatible icons and layouts.

## Suggested-code safety

Never automatically apply AI-generated code.

Deterministic fixes may be offered when they are proven to preserve behavior, but they still require confirmation.

AI suggestions must always show:

```text
This suggestion was generated by AI and may change program behavior.
Review and test it before activation.
```

The plug-in must not activate an ABAP object, create a transport or execute code.

## Eclipse build and update site

Create:

```text
eclipse-feature
eclipse-updatesite
```

Configure Maven Tycho to build:

```text
Plug-in JAR
Feature
p2 update repository
ZIP archive of the update repository
```

Keep Eclipse and ADT target-platform versions configurable through Maven properties or a target definition.

Document how to update the target platform.

Do not make the core analyzer dependent on a specific ADT release.

## GitHub Actions

Create workflows for:

### Pull requests

* Compile Java
* Run Java unit tests
* Run Python tests
* Run formatting checks
* Run static security checks
* Build the Eclipse plug-in
* Build the p2 update site

### Releases

* Build signed or checksum-verifiable release artifacts
* Create SHA-256 checksums
* Attach update-site ZIP to the GitHub release
* Publish the p2 repository to GitHub Pages
* Generate release notes

Do not commit secrets or signing passwords.

## Test data

Create realistic ABAP test examples for every rule.

Example bad performance code:

```abap
LOOP AT lt_person INTO DATA(ls_person).
  SELECT SINGLE *
    FROM pa0002
    INTO @DATA(ls_pa0002)
    WHERE pernr = @ls_person-pernr.

  WRITE: / ls_pa0002-pernr,
           ls_pa0002-nachn,
           ls_pa0002-vorna.
ENDLOOP.
```

Expected findings:

```text
PERF_SELECT_IN_LOOP
PERF_SELECT_STAR
PRIV_PERSONAL_DATA_IN_SPOOL
PRIV_BROAD_HR_MASTER_DATA_SELECTION
```

Also provide corrected example code using a bulk database read and restricted field list.

Include tests proving that:

* Comments do not create false findings.
* String literals containing ABAP keywords are ignored.
* Nested blocks are recognized.
* Line numbers are accurate.
* Empty `FOR ALL ENTRIES` checks are recognized.
* Suppressed findings require a reason.
* The AI gateway cannot alter deterministic line numbers.
* Source code is not written to logs.

## Documentation

Create:

```text
README.md
docs/architecture.md
docs/eclipse-development.md
docs/local-ollama.md
docs/rules.md
docs/privacy-model.md
docs/security-model.md
docs/releasing.md
docs/troubleshooting.md
```

The README must explain:

* What ABAP Guardian does
* What it does not guarantee
* Local-only architecture
* Installation through the Eclipse update site
* Starting the FastAPI service
* Connecting to Ollama
* Running analysis
* Creating custom rules
* Building from source
* Reporting false positives
* Security disclosure process

Clearly state:

```text
ABAP Guardian is a development-assistance tool. Its findings are not a
legal compliance determination, security certification or replacement
for SAP ATC, Code Inspector, authorization reviews, performance traces
or professional data-protection assessment.
```

## Future ATC integration

Create `docs/atc-roadmap.md`.

Describe a future optional SAP-side implementation using custom ATC checks. Do not make the Eclipse MVP dependent on this component.

The future ATC component should:

* Execute deterministic checks in the SAP system
* Avoid external AI calls during mandatory transport checks
* Produce stable rule IDs
* Support central check variants
* Optionally provide a link that opens the detailed local AI explanation

## Licensing

Use the Apache License 2.0 unless an included dependency requires a different compatible choice.

Include third-party attribution.

Do not copy code from ABAP cleaner or SAP proprietary plug-ins.

## Delivery process

Work iteratively:

1. Scaffold repository and build system.
2. Implement analyzer data model.
3. Implement tokenizer and statement model.
4. Implement initial deterministic rules.
5. Add tests.
6. Implement FastAPI and Ollama integration.
7. Implement Eclipse commands and findings view.
8. Implement annotations and comparison dialog.
9. Build feature and update site.
10. Add GitHub Actions.
11. Complete documentation.
12. Run all tests and correct build failures.

After each step, update a `PROJECT_STATUS.md` file showing:

```text
Completed
In progress
Known limitations
Next tasks
Test status
```

Do not mark a component as completed unless its tests pass.

The initial successful milestone is reached when:

* `mvn clean verify` succeeds.
* Python tests succeed.
* The p2 update-site ZIP is generated.
* The plug-in can analyze selected ABAP code.
* Findings appear with correct line numbers.
* The plug-in communicates with local Ollama through FastAPI.
* No source code is persisted or logged.
* At least 20 deterministic rules are implemented and tested.
