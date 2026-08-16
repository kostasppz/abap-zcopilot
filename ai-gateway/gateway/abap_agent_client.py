"""Client for the user's local streaming ABAP Expert RAG service.

The service exposes ``POST /api/chat`` as newline-delimited JSON. Guardian
collects token events into one response while leaving knowledge retrieval,
PDF/Word parsing, Chroma and the Ollama model inside the local ABAP agent.
No prompt content is logged.
"""

from __future__ import annotations

import json
from typing import Any

import httpx

from .config import settings


class AbapAgentError(RuntimeError):
    pass


async def _status() -> dict[str, Any]:
    try:
        async with httpx.AsyncClient(
            base_url=settings.abap_agent_base_url,
            timeout=10,
            trust_env=False,
        ) as client:
            response = await client.get("/api/status")
            response.raise_for_status()
            body = response.json()
    except (httpx.HTTPError, ValueError) as exc:
        raise AbapAgentError("Local ABAP agent is unavailable") from exc
    if not isinstance(body, dict):
        raise AbapAgentError("Local ABAP agent returned an invalid status")
    return body


async def is_available() -> bool:
    try:
        body = await _status()
    except AbapAgentError:
        return False
    return bool(body.get("ollama_online") and body.get("chat_model_available"))


async def list_models() -> list[str]:
    try:
        body = await _status()
    except AbapAgentError:
        return []
    model = body.get("chat_model")
    if body.get("chat_model_available") and isinstance(model, str) and model:
        return [model]
    return []


def _combined_message(prompt: str, system: str) -> str:
    if not system:
        return prompt
    return f"Guardian instructions:\n{system}\n\nGuardian request:\n{prompt}"


async def generate_text(prompt: str, system: str = "") -> str:
    payload = {
        "message": _combined_message(prompt, system),
        "history": [],
    }
    tokens: list[str] = []
    try:
        async with httpx.AsyncClient(
            base_url=settings.abap_agent_base_url,
            timeout=settings.ai_timeout_seconds,
            trust_env=False,
        ) as client, client.stream("POST", "/api/chat", json=payload) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if not line.strip():
                    continue
                event = json.loads(line)
                if not isinstance(event, dict):
                    continue
                event_type = event.get("type")
                if event_type == "token":
                    content = event.get("content")
                    if isinstance(content, str):
                        tokens.append(content)
                elif event_type == "error":
                    raise AbapAgentError("Local ABAP agent generation failed")
    except AbapAgentError:
        raise
    except (httpx.HTTPError, ValueError, json.JSONDecodeError) as exc:
        raise AbapAgentError("Local ABAP agent request failed") from exc

    answer = "".join(tokens).strip()
    if not answer:
        raise AbapAgentError("Local ABAP agent returned no text")
    return answer
