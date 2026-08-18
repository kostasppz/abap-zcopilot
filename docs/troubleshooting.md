# Troubleshooting

## Gateway

**`/health` shows `analyzerAvailable: false`**
Set `ANALYZER_JAR` to the built jar and ensure `java` is on PATH:

```bash
mvn -pl analyzer-core clean package
export ANALYZER_JAR=$PWD/analyzer-core/target/analyzer-core-0.5.1-SNAPSHOT.jar
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

**RunPod `/health` shows `llmAvailable: false`**
Confirm `abap-agent`, `ollama` and `guardian` are RUNNING under Supervisor and
that `/workspace/abap-stack` contains the agent, models and knowledge data.

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
Verify the Pod is running and use
`https://<POD_ID>-8001.proxy.runpod.net` in *ABAP Guardian → Configure*.
Store the matching `GUARDIAN_API_TOKEN`, then use *Test Connection*.

**The API token disappears or the Preferences buttons appear inactive**
Update ABAP Guardian to `0.5.1` or newer. The plug-in now flushes encrypted
credentials to Eclipse Secure Storage immediately, displays a confirmation or
an Error Log reference, and runs *Test Connection* as a visible background job.

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

**A topic-only analysis shows findings from every category**
Update ABAP Guardian to `0.5.1` or newer. Topic-only analysis is now enforced
by the gateway and again by Eclipse, so Performance, Security, S/4HANA and
Clean ABAP commands cannot display unrelated categories even with an older
gateway image. Updating the RunPod image is still recommended so both sides
use the same release.

**A finding has a recommendation but no suggested code**
Right-click it and choose **Generate & Review Suggested Fix…**. Guardian sends
only the affected source range to the configured private AI service, validates
that replacement ABAP was returned, and opens a side-by-side review. It never
saves or activates the object; validate syntax, ATC findings and behavior
before saving. This action requires online AI and a valid Guardian API token.

**Tycho build fails resolving the target platform**
The build needs network access to `download.eclipse.org`. Behind a proxy,
configure Maven's proxy settings; or mirror the repo and set
`-Declipse.repo.url=...`.

## Build

**`mvn clean verify` works but `-Peclipse` fails**
That is expected in offline/sandboxed environments — the default profile
deliberately excludes the Tycho modules so core development never depends on
Eclipse downloads.
