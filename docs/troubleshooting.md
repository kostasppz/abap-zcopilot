# Troubleshooting

## Gateway

**`/health` shows `analyzerAvailable: false`**
Set `ANALYZER_JAR` to the built jar and ensure `java` is on PATH:

```bash
mvn -pl analyzer-core clean package
export ANALYZER_JAR=$PWD/analyzer-core/target/analyzer-core-0.1.0-SNAPSHOT.jar
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
Start the gateway and verify the URL in *ABAP Guardian → Configure* (default
`http://localhost:8000`); use *Test Connection*.

**No findings view opens**
*Window → Show View → Other… → ABAP Guardian → Guardian Findings.*

**Tycho build fails resolving the target platform**
The build needs network access to `download.eclipse.org`. Behind a proxy,
configure Maven's proxy settings; or mirror the repo and set
`-Declipse.repo.url=...`.

## Build

**`mvn clean verify` works but `-Peclipse` fails**
That is expected in offline/sandboxed environments — the default profile
deliberately excludes the Tycho modules so core development never depends on
Eclipse downloads.
