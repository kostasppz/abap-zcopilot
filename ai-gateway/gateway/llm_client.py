"""Provider-neutral LLM client for local Ollama or hosted OpenAI.

The hosted provider is disabled unless ``ALLOW_EXTERNAL_PROVIDERS=true``.
Prompts reach this module only after the gateway's redaction step, and
OpenAI Responses requests explicitly disable response storage.
"""

from __future__ import annotations

import json
from typing import Any

import httpx

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
    if settings.llm_provider == "openai":
        return settings.openai_model
    if _uses_abap_agent():
        return settings.abap_agent_model
    return settings.ollama_model


async def is_available() -> bool:
    if settings.llm_provider == "openai":
        return bool(settings.allow_external_providers and settings.openai_api_key)
    if settings.llm_provider == "ollama":
        return await ollama_client.is_available()
    if _uses_abap_agent():
        return await abap_agent_client.is_available()
    return False


async def list_models() -> list[str]:
    if not await is_available():
        return []
    if settings.llm_provider == "openai":
        return [settings.openai_model]
    if _uses_abap_agent():
        return await abap_agent_client.list_models()
    try:
        return await ollama_client.list_models()
    except Exception:  # noqa: BLE001 - availability endpoint must degrade safely
        return []


def _extract_openai_text(body: dict[str, Any]) -> str:
    """Collect output_text parts without assuming a fixed output position."""
    texts: list[str] = []
    for item in body.get("output", []):
        if not isinstance(item, dict) or item.get("type") != "message":
            continue
        for content in item.get("content", []):
            if isinstance(content, dict) and content.get("type") == "output_text":
                text = content.get("text")
                if isinstance(text, str):
                    texts.append(text)
    return "\n".join(texts)


async def _openai_generate(prompt: str, system: str) -> str:
    if not settings.allow_external_providers:
        raise LlmError("External AI providers are disabled")
    if not settings.openai_api_key:
        raise LlmError("Hosted AI is not configured")
    payload: dict[str, Any] = {
        "model": settings.openai_model,
        "input": prompt,
        "store": False,
        "max_output_tokens": settings.max_tokens,
    }
    if system:
        payload["instructions"] = system
    headers = {
        "Authorization": f"Bearer {settings.openai_api_key}",
        "Content-Type": "application/json",
    }
    try:
        async with httpx.AsyncClient(
            base_url=settings.openai_base_url,
            timeout=settings.ai_timeout_seconds,
            headers=headers,
        ) as client:
            response = await client.post("/responses", json=payload)
            response.raise_for_status()
            text = _extract_openai_text(response.json())
    except (httpx.HTTPError, ValueError) as exc:
        raise LlmError("Hosted AI request failed") from exc
    if not text:
        raise LlmError("Hosted AI returned no text")
    return text


async def generate_text(prompt: str, system: str = "") -> str:
    if settings.llm_provider == "openai":
        return await _openai_generate(prompt, system)
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
    stripped = text.strip()
    if stripped.startswith("```") and stripped.endswith("```"):
        lines = stripped.splitlines()
        if len(lines) >= 3:
            text = "\n".join(lines[1:-1])
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        raise LlmError("AI returned non-JSON output") from exc
