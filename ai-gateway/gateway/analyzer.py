"""Bridge to the deterministic analyzer-core CLI.

Runs `java -jar analyzer-core.jar -` with the source piped via stdin, so the
source never touches the filesystem or process arguments (which could appear
in `ps` output or shell history).
"""

from __future__ import annotations

import json
import shutil
import subprocess
from pathlib import Path

from .config import settings


class AnalyzerError(RuntimeError):
    pass


def analyzer_available() -> bool:
    jar = settings.analyzer_jar
    return bool(jar) and Path(jar).is_file() and shutil.which(settings.java_bin) is not None


def run_deterministic_analysis(source: str, object_name: str, object_type: str) -> dict:
    """Run analyzer-core and return the parsed JSON result.

    Never logs or persists the source.
    """
    if not analyzer_available():
        raise AnalyzerError(
            "analyzer-core jar not configured or java missing; set ANALYZER_JAR"
        )
    cmd = [settings.java_bin, "-jar", settings.analyzer_jar, "-"]
    if settings.rules_config:
        cmd.append(settings.rules_config)
    try:
        proc = subprocess.run(
            cmd,
            input=source.encode("utf-8"),
            capture_output=True,
            timeout=settings.request_timeout_seconds,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise AnalyzerError("deterministic analysis timed out") from exc
    if proc.returncode != 0:
        # stderr may not contain source (the CLI never echoes it), but be
        # conservative and do not propagate it verbatim.
        raise AnalyzerError(f"analyzer-core failed with exit code {proc.returncode}")
    result = json.loads(proc.stdout.decode("utf-8"))
    result["objectName"] = object_name
    result["objectType"] = object_type
    return result
