# Eclipse Plug-in Development

## Building

The Eclipse modules are gated behind the `eclipse` Maven profile because
Tycho target-platform resolution downloads a large amount of data:

```bash
mvn clean verify -Peclipse
```

The target platform defaults to the Eclipse 2024-06 release repository and
can be overridden:

```bash
mvn clean verify -Peclipse -Declipse.repo.url=https://download.eclipse.org/releases/2024-12
```

Artifacts:
- Plug-in JAR: `eclipse-plugin/target/`
- p2 update site + ZIP: `eclipse-updatesite/target/`

## Installing into Eclipse / ADT

1. Build the update site (or download the release ZIP).
2. *Help → Install New Software… → Add… → Archive*, select the ZIP.
3. Select **ABAP Guardian**, finish, restart.
4. Start the gateway (`uvicorn gateway.main:app --port 8000`).
5. Configure via *ABAP Guardian → Configure* (default URL
   `http://localhost:8000`).

## Development in the IDE

Import `eclipse-plugin`, `eclipse-feature`, `eclipse-updatesite` as existing
projects into an Eclipse PDE workspace (Eclipse for RCP/RAP developers or an
ADT installation with PDE). Launch a runtime workbench with the plug-in via
*Run As → Eclipse Application*.

## Design constraints (enforced by review)

- **Public APIs only.** No imports from `.internal` packages, neither
  platform nor ADT.
- **ADT isolation.** Anything ADT-specific lives in
  `com.abapguardian.eclipse.adapter`. The rest of the plug-in depends only
  on generic text-editor interfaces, so it degrades gracefully outside ADT.
- **Non-intrusive.** The plug-in never saves documents, never activates
  ABAP objects, and applies suggested edits only after explicit user
  confirmation as one undoable edit.
- **Background work.** All gateway calls run in `org.eclipse.core.runtime.jobs.Job`s.
- **Secrets.** Only via `SecureCredentialStore` (Equinox secure storage).

## UI overview

- **Analyze Current Editor shortcut** — press `Ctrl+Alt+G` on Windows/Linux
  or `Cmd+Option+G` on macOS while an ABAP source editor is active. The
  binding can be changed under *Window → Preferences → General → Keys* by
  searching for **Analyze Current Editor**.
- **Guardian Findings view** — columns Severity | Category | Rule | Line |
  Confidence | Title; double-click navigates to the finding.
- **Editor annotations** — orange markers per finding
  (`FindingAnnotations`).
- **Compare dialog** — `SuggestedFixDialog` shows current vs suggested code
  before any change.
- **Preference page** — service URL, timeout, AI toggle, minimum severity,
  connection test.
