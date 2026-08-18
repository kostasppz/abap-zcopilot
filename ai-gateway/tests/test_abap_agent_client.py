from __future__ import annotations

import asyncio
import json
from unittest.mock import patch

import pytest
import respx
from httpx import Response

from gateway import abap_agent_client, llm_client
from gateway.config import settings


@respx.mock
def test_abap_agent_availability_uses_local_status():
    respx.get(f"{settings.abap_agent_base_url}/api/status").mock(
        return_value=Response(
            200,
            json={
                "ollama_online": True,
                "chat_model": "abap-expert",
                "chat_model_available": True,
            },
        )
    )
    with patch.object(settings, "llm_provider", "abap-agent"):
        assert asyncio.run(llm_client.is_available()) is True
        assert asyncio.run(llm_client.list_models()) == ["abap-expert"]
        assert llm_client.provider_name() == "abap-agent"


@respx.mock
def test_abap_agent_collects_streamed_ndjson_tokens():
    events = [
        {"type": "status", "message": "Searching local knowledge"},
        {"type": "sources", "sources": [{"source": "hr.pdf", "chunk": 2}]},
        {"type": "token", "content": "Use a "},
        {"type": "token", "content": "hashed table."},
        {"type": "done"},
    ]
    route = respx.post(f"{settings.abap_agent_base_url}/api/chat").mock(
        return_value=Response(
            200,
            text="".join(json.dumps(event) + "\n" for event in events),
            headers={"content-type": "application/x-ndjson"},
        )
    )

    answer = asyncio.run(
        abap_agent_client.generate_text("How should I define this table?", "Answer ABAP only.")
    )

    assert answer == "Use a hashed table."
    sent = json.loads(route.calls[0].request.content)
    assert "Answer ABAP only" in sent["message"]
    assert "How should I define this table" in sent["message"]
    assert sent["history"] == []


@respx.mock
def test_abap_agent_error_event_is_not_exposed():
    respx.post(f"{settings.abap_agent_base_url}/api/chat").mock(
        return_value=Response(
            200,
            text=json.dumps({"type": "error", "message": "secret backend detail"}) + "\n",
        )
    )
    with pytest.raises(abap_agent_client.AbapAgentError, match="generation failed"):
        asyncio.run(abap_agent_client.generate_text("question"))


@respx.mock
def test_abap_agent_json_accepts_one_fenced_document():
    event = {
        "type": "token",
        "content": '```json\n{"suggestedCode": "READ TABLE lt.", "caveats": "Review"}\n```',
    }
    respx.post(f"{settings.abap_agent_base_url}/api/chat").mock(
        return_value=Response(200, text=json.dumps(event) + "\n")
    )
    with patch.object(settings, "llm_provider", "abap-agent"):
        result = asyncio.run(llm_client.generate_json("Return JSON"))
    assert result["suggestedCode"] == "READ TABLE lt."


def test_llm_client_extracts_json_surrounded_by_model_commentary():
    response = (
        "Here is the requested correction:\n"
        '{"suggestedCode":"DATA(result) = value.","caveats":"Run ATC"}'
        "\nPlease review it."
    )
    with patch.object(settings, "llm_provider", "abap-agent"), \
         patch("gateway.llm_client.generate_text", return_value=response):
        result = asyncio.run(llm_client.generate_json("prompt"))
    assert result["suggestedCode"] == "DATA(result) = value."


def test_llm_client_json_extractor_handles_braces_inside_strings():
    response = 'answer: {"suggestedCode":"IF value = \\"{\\". ENDIF.","caveats":""} done'
    with patch.object(settings, "llm_provider", "abap-agent"), \
         patch("gateway.llm_client.generate_text", return_value=response):
        result = asyncio.run(llm_client.generate_json("prompt"))
    assert '"{"' in result["suggestedCode"]
