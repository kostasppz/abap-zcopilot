"""Prove that ABAP source never appears in gateway logs."""

from __future__ import annotations

import logging
from unittest.mock import patch

MARKER = "ZZ_SECRET_SOURCE_MARKER_lv_password"
ABAP = f"DATA {MARKER} TYPE string.\nWRITE {MARKER}.\n"


def test_source_never_logged(client, deterministic_result, caplog):
    caplog.set_level(logging.DEBUG)
    with patch("gateway.analyzer.run_deterministic_analysis", return_value=deterministic_result), \
         patch("gateway.llm_client.is_available", return_value=False):
        resp = client.post("/api/v1/analyze", json={"source": ABAP})
    assert resp.status_code == 200
    assert MARKER not in caplog.text


def test_source_not_passed_via_command_line(monkeypatch, tmp_path):
    """The analyzer bridge must pipe source via stdin, never argv."""
    from gateway import analyzer

    jar = tmp_path / "analyzer.jar"
    jar.write_bytes(b"fake")
    monkeypatch.setattr(analyzer.settings, "analyzer_jar", str(jar))

    captured: dict = {}

    def fake_run(cmd, **kwargs):
        captured["cmd"] = cmd
        captured["input"] = kwargs.get("input")

        class R:
            returncode = 0
            stdout = b'{"objectName":"X","objectType":"PROG","findings":[],"suppressedFindings":[]}'
            stderr = b""

        return R()

    with patch("gateway.analyzer.shutil.which", return_value="/usr/bin/java"), \
         patch("gateway.analyzer.subprocess.run", side_effect=fake_run):
        analyzer.run_deterministic_analysis(ABAP, "X", "PROG")

    assert all(MARKER not in part for part in captured["cmd"])
    assert MARKER.encode() in captured["input"]


def test_analyzer_error_does_not_leak_source(monkeypatch, tmp_path):
    from gateway import analyzer

    jar = tmp_path / "analyzer.jar"
    jar.write_bytes(b"fake")
    monkeypatch.setattr(analyzer.settings, "analyzer_jar", str(jar))

    def fake_run(cmd, **kwargs):
        class R:
            returncode = 1
            stdout = b""
            stderr = ABAP.encode()  # hostile case: source echoed to stderr

        return R()

    with patch("gateway.analyzer.shutil.which", return_value="/usr/bin/java"), \
         patch("gateway.analyzer.subprocess.run", side_effect=fake_run):
        try:
            analyzer.run_deterministic_analysis(ABAP, "X", "PROG")
            raise AssertionError("expected AnalyzerError")
        except analyzer.AnalyzerError as exc:
            assert MARKER not in str(exc)
