"""Tests for the local vector knowledge provider (RAG)."""

from __future__ import annotations

import asyncio
import json
from unittest.mock import patch

import respx
from httpx import Response

from gateway.config import settings
from gateway.knowledge import (
    BundledKnowledgeProvider,
    LocalVectorKnowledgeProvider,
    NoOpKnowledgeProvider,
    get_default_provider,
)


def _write_index(tmp_path, snippets, model="nomic-embed-text"):
    path = tmp_path / "index.json"
    path.write_text(json.dumps({"embeddingModel": model, "snippets": snippets}))
    return str(path)


INDEX_SNIPPETS = [
    {
        "source": "rules.md#Performance",
        "content": "PERF_SELECT_IN_LOOP: Any SELECT inside a LOOP multiplies round trips.",
        "ruleIds": ["PERF_SELECT_IN_LOOP"],
        "embedding": [1.0, 0.0, 0.0],
    },
    {
        "source": "abap-best-practices.md#Secrets",
        "content": "Credentials in source end up in transports.",
        "ruleIds": ["SEC_HARDCODED_PASSWORD"],
        "embedding": [0.0, 1.0, 0.0],
    },
    {
        "source": "abap-best-practices.md#FAE",
        "content": "Empty driver table selects everything.",
        "ruleIds": ["PERF_FOR_ALL_ENTRIES_WITHOUT_EMPTY_CHECK"],
        "embedding": [0.9, 0.1, 0.0],
    },
]


@respx.mock
def test_retrieval_ranks_by_similarity_and_rule_boost(tmp_path):
    respx.post(f"{settings.ollama_base_url}/api/embeddings").mock(
        return_value=Response(200, json={"embedding": [0.9, 0.1, 0.0]})
    )
    provider = LocalVectorKnowledgeProvider(_write_index(tmp_path, INDEX_SNIPPETS))
    results = provider.retrieve("PERF_SELECT_IN_LOOP in a loop", limit=2)
    assert len(results) == 2
    # Exact rule-ID match outranks the closer embedding.
    assert results[0].source == "rules.md#Performance"
    assert results[0].score > results[1].score
    assert results[1].source == "abap-best-practices.md#FAE"


@respx.mock
def test_retrieval_respects_limit(tmp_path):
    respx.post(f"{settings.ollama_base_url}/api/embeddings").mock(
        return_value=Response(200, json={"embedding": [0.5, 0.5, 0.5]})
    )
    provider = LocalVectorKnowledgeProvider(_write_index(tmp_path, INDEX_SNIPPETS))
    assert len(provider.retrieve("anything", limit=1)) == 1
    assert provider.retrieve("anything", limit=0) == []


@respx.mock
def test_keyword_fallback_when_embeddings_unavailable(tmp_path):
    respx.post(f"{settings.ollama_base_url}/api/embeddings").mock(
        return_value=Response(500)
    )
    provider = LocalVectorKnowledgeProvider(_write_index(tmp_path, INDEX_SNIPPETS))
    results = provider.retrieve("SEC_HARDCODED_PASSWORD", limit=3)
    assert [r.source for r in results] == ["abap-best-practices.md#Secrets"]


@respx.mock
def test_no_match_returns_empty(tmp_path):
    respx.post(f"{settings.ollama_base_url}/api/embeddings").mock(
        return_value=Response(500)
    )
    provider = LocalVectorKnowledgeProvider(_write_index(tmp_path, INDEX_SNIPPETS))
    assert provider.retrieve("PRIV_UNKNOWN_RULE_XYZ") == []


def test_corrupt_index_returns_empty(tmp_path):
    path = tmp_path / "bad.json"
    path.write_text("{not json")
    assert LocalVectorKnowledgeProvider(str(path)).retrieve("PERF_SELECT_IN_LOOP") == []


def test_default_provider_is_noop_without_index():
    with patch.object(settings, "knowledge_index_path", ""), \
         patch.object(settings, "bundled_knowledge_path", ""):
        assert isinstance(get_default_provider(), NoOpKnowledgeProvider)


def test_default_provider_uses_local_vector_when_index_exists(tmp_path):
    path = _write_index(tmp_path, INDEX_SNIPPETS)
    with patch.object(settings, "knowledge_index_path", path):
        provider = get_default_provider()
    assert isinstance(provider, LocalVectorKnowledgeProvider)
    assert provider.index_path == path


def test_default_provider_uses_bundled_docs_without_vector_index(tmp_path):
    (tmp_path / "rules.md").write_text("PERF_SELECT_IN_LOOP avoid selects in loops")
    with patch.object(settings, "knowledge_index_path", ""), \
         patch.object(settings, "bundled_knowledge_path", str(tmp_path)):
        provider = get_default_provider()
    assert isinstance(provider, BundledKnowledgeProvider)
    assert provider.retrieve("PERF_SELECT_IN_LOOP")


@respx.mock
def test_enhancement_injects_retrieved_snippets_behind_redaction(tmp_path, sample_finding):
    from gateway.enhancement import enhance_findings
    from gateway.schemas import Finding

    snippets = [dict(INDEX_SNIPPETS[0])]
    snippets[0]["content"] = (
        "PERF_SELECT_IN_LOOP guidance for john.doe@example.com: batch your reads."
    )
    respx.post(f"{settings.ollama_base_url}/api/embeddings").mock(
        return_value=Response(500)  # keyword fallback path
    )
    route = respx.post(f"{settings.ollama_base_url}/api/generate").mock(
        return_value=Response(200, json={"response": "[]"})
    )
    provider = LocalVectorKnowledgeProvider(_write_index(tmp_path, snippets))
    finding = Finding.model_validate(sample_finding)
    result = asyncio.run(enhance_findings([finding], "LOOP AT lt.", provider=provider))
    assert result == [finding]  # AI returned nothing usable; finding unchanged
    sent = route.calls[0].request.content.decode()
    assert "batch your reads" in sent
    # Retrieved knowledge passes through the redaction layer.
    assert "john.doe@example.com" not in sent


@respx.mock
def test_enhancement_still_works_without_knowledge(sample_finding):
    from gateway.enhancement import enhance_findings
    from gateway.schemas import Finding

    route = respx.post(f"{settings.ollama_base_url}/api/generate").mock(
        return_value=Response(
            200,
            json={
                "response": json.dumps(
                    [
                        {
                            "ruleId": "PERF_SELECT_IN_LOOP",
                            "explanation": "Better explanation.",
                            "recommendation": None,
                            "suggestedCode": None,
                        }
                    ]
                )
            },
        )
    )
    finding = Finding.model_validate(sample_finding)
    with patch.object(settings, "knowledge_index_path", ""), \
         patch.object(settings, "bundled_knowledge_path", ""):
        result = asyncio.run(enhance_findings([finding], "LOOP AT lt."))
    assert result[0].explanation == "Better explanation."
    assert result[0].startLine == finding.startLine
    assert "Relevant knowledge" not in route.calls[0].request.content.decode()
