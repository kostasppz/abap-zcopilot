from __future__ import annotations

from unittest.mock import patch

import respx
from httpx import Response

from gateway.config import settings

ABAP = "LOOP AT lt INTO ls.\n  SELECT SINGLE * FROM pa0002 INTO ls_p.\nENDLOOP.\n"


def test_analyze_returns_deterministic_findings_without_ai(client, deterministic_result):
    with patch("gateway.analyzer.run_deterministic_analysis", return_value=deterministic_result), \
         patch("gateway.llm_client.is_available", return_value=False):
        resp = client.post(
            "/api/v1/analyze",
            json={"source": ABAP, "objectName": "ZTEST", "useAi": True},
        )
    assert resp.status_code == 200
    body = resp.json()
    assert body["aiEnhanced"] is False
    assert body["findings"][0]["ruleId"] == "PERF_SELECT_IN_LOOP"
    assert body["findings"][0]["startLine"] == 3


@respx.mock
def test_ai_cannot_alter_line_numbers(client, deterministic_result):
    # AI replies with a bogus line number and a new invented finding — both
    # must be ignored; only text fields may change.
    respx.get(f"{settings.ollama_base_url}/api/tags").mock(
        return_value=Response(200, json={"models": []})
    )
    respx.post(f"{settings.ollama_base_url}/api/generate").mock(
        return_value=Response(
            200,
            json={
                "response": (
                    '[{"ruleId": "PERF_SELECT_IN_LOOP", "explanation": "Better text",'
                    ' "startLine": 999},'
                    ' {"ruleId": "FAKE_INVENTED_RULE", "explanation": "made up"}]'
                )
            },
        )
    )
    with patch("gateway.analyzer.run_deterministic_analysis", return_value=deterministic_result):
        resp = client.post("/api/v1/analyze", json={"source": ABAP, "useAi": True})
    assert resp.status_code == 200
    body = resp.json()
    assert body["aiEnhanced"] is True
    assert len(body["findings"]) == 1  # invented finding was not added
    f = body["findings"][0]
    assert f["startLine"] == 3  # line numbers untouched
    assert f["explanation"] == "Better text"  # text was enhanced


@respx.mock
def test_invalid_ai_json_leaves_findings_untouched(client, deterministic_result):
    respx.get(f"{settings.ollama_base_url}/api/tags").mock(
        return_value=Response(200, json={"models": []})
    )
    respx.post(f"{settings.ollama_base_url}/api/generate").mock(
        return_value=Response(200, json={"response": "this is not json"})
    )
    with patch("gateway.analyzer.run_deterministic_analysis", return_value=deterministic_result):
        resp = client.post("/api/v1/analyze", json={"source": ABAP, "useAi": True})
    assert resp.status_code == 200
    f = resp.json()["findings"][0]
    assert f["explanation"] == "A database SELECT runs on every loop iteration."


def test_source_length_limit(client):
    big = "x" * (settings.max_source_length + 1)
    resp = client.post("/api/v1/analyze", json={"source": big})
    assert resp.status_code == 413


def test_empty_source_rejected(client):
    resp = client.post("/api/v1/analyze", json={"source": "   "})
    assert resp.status_code == 422


def test_analyzer_unavailable_returns_503(client):
    with patch("gateway.analyzer.analyzer_available", return_value=False):
        resp = client.post("/api/v1/analyze", json={"source": ABAP})
    assert resp.status_code == 503


def test_max_findings_limit(client, sample_finding):
    many = {
        "objectName": "Z",
        "objectType": "PROG",
        "findings": [dict(sample_finding) for _ in range(settings.max_findings + 50)],
        "suppressedFindings": [],
    }
    with patch("gateway.analyzer.run_deterministic_analysis", return_value=many), \
         patch("gateway.llm_client.is_available", return_value=False):
        resp = client.post("/api/v1/analyze", json={"source": ABAP, "useAi": False})
    assert len(resp.json()["findings"]) == settings.max_findings


def test_analyze_filters_requested_categories(client, sample_finding):
    security_finding = dict(sample_finding)
    security_finding["ruleId"] = "SEC_HARDCODED_PASSWORD"
    security_finding["category"] = "SECURITY"
    deterministic = {
        "objectName": "ZTEST",
        "objectType": "PROG",
        "findings": [sample_finding, security_finding],
        "suppressedFindings": [],
    }
    with patch("gateway.analyzer.run_deterministic_analysis", return_value=deterministic):
        resp = client.post(
            "/api/v1/analyze",
            json={"source": ABAP, "useAi": False, "categories": ["SECURITY"]},
        )
    assert resp.status_code == 200
    assert [item["category"] for item in resp.json()["findings"]] == ["SECURITY"]


def test_analyze_rejects_unknown_category(client):
    resp = client.post(
        "/api/v1/analyze",
        json={"source": ABAP, "categories": ["NOT_A_CATEGORY"]},
    )
    assert resp.status_code == 422
