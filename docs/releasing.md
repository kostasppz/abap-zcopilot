# Releasing

Releases are driven by tags and the `release.yml` workflow.

## Steps

1. Ensure `main` is green (PR workflow: Java tests, Python tests, lint,
   security checks, Tycho build).
2. Bump versions if needed:
   - Maven: `mvn versions:set -DnewVersion=X.Y.Z` (+ Tycho versions in
     MANIFEST.MF / feature.xml — keep `.qualifier` suffixes).
   - Python: `version` in `ai-gateway/pyproject.toml` and
     `gateway/__init__.py`.
3. Update `PROJECT_STATUS.md` and commit.
4. Verify the hosted service URL in
   `GuardianPreferences.DEFAULT_SERVICE_URL` and deploy `render.yaml` if the
   service is not already live. Store `OPENAI_API_KEY` only in the host.
5. Tag and push:

   ```bash
   git tag vX.Y.Z
   git push origin vX.Y.Z
   ```

## What the workflow does

- Builds analyzer-core and the Eclipse modules (`-Peclipse`).
- Packages the p2 update-site ZIP and the analyzer JAR into `dist/`.
- Generates `SHA256SUMS.txt` checksums.
- Creates a GitHub release with generated notes (commit log since the
  previous tag) and attaches the artifacts.
- Publishes the p2 repository to GitHub Pages, so users can install from
  `https://<org>.github.io/<repo>/` as an update-site URL.

The release workflow publishes the Eclipse update site; it does not host the
FastAPI runtime. Render (or an organization-owned container platform) builds
and runs the root `Dockerfile` separately.

No secrets are stored in the repository; the workflow uses the ephemeral
`GITHUB_TOKEN` only.
