"""LLM client for the private RunPod ABAP Agent or its local Ollama runtime."""

from __future__ import annotations

import json
from typing import Any

from . import abap_agent_client, ollama_client
from .config import settings


class LlmError(RuntimeError):
    pass


_ABAP_AGENT_PROVIDERS = {"abap-agent", "abap_agent"}


def _uses_abap_agent() -> bool:
    return settings.llm_provider in _ABAP_AGENT_PROVIDERS


def provider_name() -> str:
    if _uses_abap_agent():
        return "abap-agent"
    return settings.llm_provider


def model_name() -> str:
    if _uses_abap_agent():
        return settings.abap_agent_model
    return settings.ollama_model


async def is_available() -> bool:
    if settings.llm_provider == "ollama":
        return await ollama_client.is_available()
    if _uses_abap_agent():
        return await abap_agent_client.is_available()
    return False


async def list_models() -> list[str]:
    if not await is_available():
        return []
    if _uses_abap_agent():
        return await abap_agent_client.list_models()
    try:
        return await ollama_client.list_models()
    except Exception:  # noqa: BLE001 - availability endpoint must degrade safely
        return []


async def generate_text(prompt: str, system: str = "") -> str:
    if settings.llm_provider == "ollama":
        try:
            return await ollama_client.generate_text(prompt, system)
        except ollama_client.OllamaError as exc:
            raise LlmError("Local AI request failed") from exc
    if _uses_abap_agent():
        try:
            return await abap_agent_client.generate_text(prompt, system)
        except abap_agent_client.AbapAgentError as exc:
            raise LlmError("Local ABAP agent request failed") from exc
    raise LlmError(f"Unsupported LLM provider: {settings.llm_provider}")


async def generate_json(prompt: str, system: str = "") -> Any:
    if settings.llm_provider == "ollama":
        try:
            return await ollama_client.generate_json(prompt, system)
        except ollama_client.OllamaError as exc:
            raise LlmError("Local AI request failed") from exc
    text = await generate_text(prompt, system)
    text = _extract_json_text(text)
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        raise LlmError("AI returned non-JSON output") from exc


def _extract_json_text(text: str) -> str:
    """Extract one JSON object/array from common model response wrappers.

    Models occasionally surround an otherwise valid response with Markdown or
    a short explanation. This scanner accepts that presentation noise while
    still requiring the extracted value itself to pass strict ``json.loads``.
    """
    stripped = text.strip()
    if stripped.startswith("```") and stripped.endswith("```"):
        lines = stripped.splitlines()
        if len(lines) >= 3:
            stripped = "\n".join(lines[1:-1]).strip()
    try:
        json.loads(stripped)
        return stripped
    except json.JSONDecodeError:
        pass

    for start, opening in enumerate(stripped):
        if opening not in "[{":
            continue
        closing = "]" if opening == "[" else "}"
        depth = 0
        in_string = False
        escaped = False
        for end in range(start, len(stripped)):
            char = stripped[end]
            if in_string:
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    in_string = False
                continue
            if char == '"':
                in_string = True
            elif char == opening:
                depth += 1
            elif char == closing:
                depth -= 1
                if depth == 0:
                    candidate = stripped[start : end + 1]
                    try:
                        json.loads(candidate)
                        return candidate
                    except json.JSONDecodeError:
                        break
    return stripped
