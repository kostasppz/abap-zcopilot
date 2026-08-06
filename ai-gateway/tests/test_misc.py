from __future__ import annotations

from unittest.mock import patch

import respx
from httpx import Response

from gateway.config import settings
from gateway.knowledge import (
    KnowledgeProvider,
    KnowledgeSnippet,
    LocalVectorKnowledgeProvider,
    NoOpKnowledgeProvider,
)
from gateway.redaction import redact


def test_health(client):
    with patch("gateway.ollama_client.is_available", return_value=True), \
         patch("gateway.analyzer.analyzer_available", return_value=True):
        resp = client.get("/health")
    body = resp.json()
    assert resp.status_code == 200
    assert body["status"] == "ok"
    assert body["ollamaAvailable"] is True


@respx.mock
def test_models_endpoint(client):
    respx.get(f"{settings.ollama_base_url}/api/tags").mock(
        return_value=Response(200, json={"models": [{"name": "gemma4:e4b"}]})
    )
    resp = client.get("/api/v1/models")
    assert resp.status_code == 200
    assert resp.json()["models"] == ["gemma4:e4b"]
    assert resp.json()["default"] == settings.ollama_model


@respx.mock
def test_explain_endpoint(client, sample_finding):
    respx.post(f"{settings.ollama_base_url}/api/generate").mock(
        return_value=Response(200, json={"response": "Because loops multiply DB round trips."})
    )
    resp = client.post(
        "/api/v1/explain",
        json={"finding": sample_finding, "sourceSnippet": "LOOP AT lt."},
    )
    assert resp.status_code == 200
    assert "round trips" in resp.json()["explanation"]


@respx.mock
def test_suggest_fix_requires_valid_schema(client, sample_finding):
    respx.post(f"{settings.ollama_base_url}/api/generate").mock(
        return_value=Response(200, json={"response": '{"unexpected": "shape"}'})
    )
    resp = client.post("/api/v1/suggest-fix", json={"finding": sample_finding})
    assert resp.status_code == 502


@respx.mock
def test_suggest_fix_marks_human_review(client, sample_finding):
    respx.post(f"{settings.ollama_base_url}/api/generate").mock(
        return_value=Response(
            200,
            json={"response": '{"suggestedCode": "SELECT pernr FROM pa0002 ...", "caveats": "check"}'},
        )
    )
    resp = client.post("/api/v1/suggest-fix", json={"finding": sample_finding})
    assert resp.status_code == 200
    assert resp.json()["requiresHumanReview"] is True


def test_redaction_masks_pii_and_credentials():
    text = (
        "pernr 12345678 mail john.doe@example.com iban DE89370400440532013000 "
        "password = 'hunter2' Authorization: Bearer abc.def.ghi"
    )
    redacted = redact(text)
    assert "12345678" not in redacted
    assert "john.doe@example.com" not in redacted
    assert "DE89370400440532013000" not in redacted
    assert "hunter2" not in redacted
    assert "abc.def.ghi" not in redacted


def test_noop_knowledge_provider_satisfies_protocol():
    provider = NoOpKnowledgeProvider()
    assert isinstance(provider, KnowledgeProvider)
    assert provider.retrieve("PERF_SELECT_IN_LOOP") == []


def test_local_vector_provider_without_index_returns_nothing():
    provider = LocalVectorKnowledgeProvider("/nonexistent/index.json")
    assert isinstance(provider, KnowledgeProvider)
    assert provider.retrieve("PERF_SELECT_IN_LOOP query") == []


class MockRagProvider:
    """Demonstrates how a future RAG provider plugs in (mock test)."""

    def retrieve(self, query: str, limit: int = 3):
        return [KnowledgeSnippet(source="rules/performance.yaml", content="Avoid SELECT in loops.", score=0.9)]


@respx.mock
def test_enhancement_uses_knowledge_provider(sample_finding):
    import asyncio

    from gateway.enhancement import enhance_findings
    from gateway.schemas import Finding

    route = respx.post(f"{settings.ollama_base_url}/api/generate").mock(
        return_value=Response(200, json={"response": "[]"})
    )
    finding = Finding.model_validate(sample_finding)
    asyncio.run(enhance_findings([finding], "snippet", provider=MockRagProvider()))
    sent = route.calls[0].request.content.decode()
    assert "Avoid SELECT in loops." in sent
