# Troubleshooting

## Gateway

**`/health` shows `analyzerAvailable: false`**
Set `ANALYZER_JAR` to the built jar and ensure `java` is on PATH:

```bash
mvn -pl analyzer-core clean package
export ANALYZER_JAR=$PWD/analyzer-core/target/analyzer-core-0.4.0-SNAPSHOT.jar
```

**`/analyze` returns 503**
The deterministic analyzer could not run — see above; also check
`REQUEST_TIMEOUT_SECONDS` for very large sources.

**`/analyze` returns 413**
Source exceeds `MAX_SOURCE_LENGTH` (default 200 000 chars). Raise the env
var or analyze smaller units.

**`ollamaAvailable: false` / no AI enhancement**
Ollama is not running or the model is missing:

```bash
ollama serve
ollama pull gemma4:e4b
```

Deterministic analysis works regardless.

**Hosted `/health` shows `llmAvailable: false`**
Confirm the service has `LLM_PROVIDER=openai`,
`ALLOW_EXTERNAL_PROVIDERS=true`, and a valid secret `OPENAI_API_KEY`. The key
must exist only in the hosting secret store.

**AI output looks unchanged**
The model's JSON failed schema validation, so the deterministic texts were
kept. That is by design — invalid AI output is dropped, never guessed.

## Analyzer

**A finding points at a slightly odd column**
Positions refer to the first token of the offending statement. For chained
statements (`WRITE: a, b.`) each chain element is a separate statement.

**Suppression doesn't work**
The reason is mandatory and the pseudo-comment must sit on the statement's
line (±1):

```abap
WRITE lv_pernr. "#EC ABAP_GUARDIAN: PRIV_UNMASKED_PERSONNEL_NUMBER reason="approved"
```

## Eclipse plug-in

**"Cannot reach ABAP Guardian gateway"**
Verify the hosted deployment is healthy and the URL in *ABAP Guardian →
Configure* (default `https://abap-zcopilot.onrender.com`); use *Test
Connection*. Free proof-of-concept instances may need time to wake up.

**No findings view opens**
*Window → Show View → Other… → ABAP Guardian → Guardian Findings.*

**Copilot is not visible next to Problems**
Use *ABAP Guardian → Open ABAP Guardian Copilot* or press `Ctrl+Alt+C`.
Perspective extensions determine the initial placement; an existing heavily
customized perspective can be restored with *Window → Perspective → Reset
Perspective…* and the view can always be dragged next to Problems manually.

**Live findings do not run while typing**
They are disabled by default. Open *Window → Preferences → ABAP Guardian*,
enable live analysis and choose a delay of at least 1000 ms. Analyze-on-save
and automatic online-AI enhancement are separate switches.

**A finding is listed but not underlined**
Re-run analysis after editing. Stale results are intentionally discarded; an
annotation is never guessed when its deterministic line range is invalid.

**Review Suggested Fix says no suggested code is available**
The deterministic rule supplied a recommendation but no replacement block,
and AI enhancement did not produce valid suggested code. Use **Suggest
Correction** in Copilot and review the answer manually; Guardian never applies
chat output automatically.

**Tycho build fails resolving the target platform**
The build needs network access to `download.eclipse.org`. Behind a proxy,
configure Maven's proxy settings; or mirror the repo and set
`-Declipse.repo.url=...`.

## Build

**`mvn clean verify` works but `-Peclipse` fails**
That is expected in offline/sandboxed environments — the default profile
deliberately excludes the Tycho modules so core development never depends on
Eclipse downloads.
