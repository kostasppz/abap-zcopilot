from __future__ import annotations

from unittest.mock import patch

import respx
from httpx import Response

from gateway.config import settings
from gateway.knowledge import (
    BundledKnowledgeProvider,
    KnowledgeProvider,
    KnowledgeSnippet,
    LocalVectorKnowledgeProvider,
    NoOpKnowledgeProvider,
)
from gateway.redaction import redact


def test_health(client):
    with patch("gateway.llm_client.is_available", return_value=True), \
         patch("gateway.analyzer.analyzer_available", return_value=True):
        resp = client.get("/health")
    body = resp.json()
    assert resp.status_code == 200
    assert body["status"] == "ok"
    assert body["llmAvailable"] is True
    assert body["llmProvider"] == "ollama"
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
def test_suggest_fix_rejects_blank_code(client, sample_finding):
    respx.post(f"{settings.ollama_base_url}/api/generate").mock(
        return_value=Response(
            200,
            json={"response": '{"suggestedCode": "  ", "caveats": "manual"}'},
        )
    )
    resp = client.post(
        "/api/v1/suggest-fix",
        json={"finding": sample_finding, "sourceSnippet": "SELECT * FROM mara."},
    )
    assert resp.status_code == 502


def test_suggest_fix_returns_existing_deterministic_code_without_ai(client, sample_finding):
    finding = dict(sample_finding)
    finding["suggestedCode"] = (
        "```abap\nSELECT matnr FROM mara INTO TABLE @DATA(materials).\n```"
    )
    with patch("gateway.llm_client.generate_json") as generate_json:
        resp = client.post(
            "/api/v1/suggest-fix",
            json={"finding": finding, "sourceSnippet": "SELECT * FROM mara."},
        )
    assert resp.status_code == 200
    assert resp.json()["suggestedCode"].startswith("SELECT matnr")
    assert resp.json()["model"] == "deterministic"
    generate_json.assert_not_called()


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


def test_bundled_knowledge_provider_retrieves_repository_docs(tmp_path):
    docs = tmp_path / "docs"
    docs.mkdir()
    (docs / "performance.md").write_text(
        "# Performance\n\nPERF_SELECT_IN_LOOP: Avoid database SELECT statements inside LOOP blocks.",
        encoding="utf-8",
    )
    provider = BundledKnowledgeProvider(str(tmp_path))
    result = provider.retrieve("How do I fix PERF_SELECT_IN_LOOP?", limit=2)
    assert result
    assert result[0].source == "docs/performance.md"
    assert "Avoid database SELECT" in result[0].content


@respx.mock
def test_chat_uses_context_and_bundled_knowledge(client, tmp_path):
    (tmp_path / "abap.md").write_text(
        "Use a bulk SELECT before a LOOP to avoid repeated database round trips.",
        encoding="utf-8",
    )
    route = respx.post(f"{settings.ollama_base_url}/api/generate").mock(
        return_value=Response(200, json={"response": "Move the SELECT before the LOOP."})
    )
    respx.get(f"{settings.ollama_base_url}/api/tags").mock(
        return_value=Response(200, json={"models": [{"name": settings.ollama_model}]})
    )
    with patch.object(settings, "bundled_knowledge_path", str(tmp_path)):
        resp = client.post(
            "/api/v1/chat",
            json={
                "question": "How can I improve this loop?",
                "objectName": "ZTEST",
                "source": "LOOP AT lt. SELECT SINGLE * FROM mara. ENDLOOP.",
            },
        )
    assert resp.status_code == 200
    body = resp.json()
    assert body["answer"] == "Move the SELECT before the LOOP."
    assert body["contextIncluded"] is True
    assert body["knowledgeReferences"] == ["abap.md"]
    sent = route.calls[0].request.content.decode()
    assert "bulk SELECT before a LOOP" in sent
    assert "LOOP AT lt" in sent


def test_chat_rejects_blank_question(client):
    resp = client.post("/api/v1/chat", json={"question": "   "})
    assert resp.status_code == 422


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
