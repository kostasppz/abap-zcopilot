"""Thin async client for a local Ollama server.

No source code or prompt content is ever logged.
"""

from __future__ import annotations

import json
from typing import Any

import httpx

from .config import settings


class OllamaError(RuntimeError):
    pass


async def list_models() -> list[str]:
    async with httpx.AsyncClient(base_url=settings.ollama_base_url, timeout=10) as client:
        resp = await client.get("/api/tags")
        resp.raise_for_status()
        data = resp.json()
        return [m["name"] for m in data.get("models", [])]


async def is_available() -> bool:
    try:
        async with httpx.AsyncClient(base_url=settings.ollama_base_url, timeout=3) as client:
            resp = await client.get("/api/tags")
            return resp.status_code == 200
    except httpx.HTTPError:
        return False


async def generate_json(prompt: str, system: str = "") -> Any:
    """Ask the model for a JSON reply and parse it. Raises OllamaError on failure."""
    payload = {
        "model": settings.ollama_model,
        "prompt": prompt,
        "system": system,
        "format": "json",
        "stream": False,
        "options": {"num_predict": settings.max_tokens},
    }
    try:
        async with httpx.AsyncClient(
            base_url=settings.ollama_base_url, timeout=settings.ai_timeout_seconds
        ) as client:
            resp = await client.post("/api/generate", json=payload)
            resp.raise_for_status()
            body = resp.json()
    except httpx.HTTPError as exc:
        raise OllamaError("Ollama request failed") from exc
    try:
        return json.loads(body.get("response", ""))
    except json.JSONDecodeError as exc:
        raise OllamaError("Ollama returned non-JSON output") from exc


async def generate_text(prompt: str, system: str = "") -> str:
    payload = {
        "model": settings.ollama_model,
        "prompt": prompt,
        "system": system,
        "stream": False,
        "options": {"num_predict": settings.max_tokens},
    }
    try:
        async with httpx.AsyncClient(
            base_url=settings.ollama_base_url, timeout=settings.ai_timeout_seconds
        ) as client:
            resp = await client.post("/api/generate", json=payload)
            resp.raise_for_status()
            return resp.json().get("response", "")
    except httpx.HTTPError as exc:
        raise OllamaError("Ollama request failed") from exc
