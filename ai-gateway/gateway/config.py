"""Gateway configuration.

Everything is local by default. External AI providers are disabled unless
explicitly enabled, and even then requests pass through the redaction layer.
Source code is never stored or logged.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path


def _int_env(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, default))
    except ValueError:
        return default


def _bool_env(name: str, default: bool) -> bool:
    value = os.environ.get(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _secret_env(name: str) -> str:
    """Read a secret from NAME_FILE first, then NAME.

    Docker Compose mounts production secrets as files. Direct environment
    variables remain available for local development and test deployments.
    """
    secret_file = os.environ.get(f"{name}_FILE", "").strip()
    if secret_file:
        try:
            return Path(secret_file).read_text(encoding="utf-8").strip()
        except OSError:
            return ""
    return os.environ.get(name, "").strip()


@dataclass
class Settings:
    # Public API authentication. Local development remains backwards
    # compatible when both values are unset. Production deployments set
    # REQUIRE_API_AUTH=true and provide GUARDIAN_API_TOKEN_FILE.
    api_token: str = field(default_factory=lambda: _secret_env("GUARDIAN_API_TOKEN"))
    require_api_auth: bool = field(
        default_factory=lambda: _bool_env("REQUIRE_API_AUTH", False)
    )
    rate_limit_per_minute: int = field(
        default_factory=lambda: _int_env("RATE_LIMIT_PER_MINUTE", 0)
    )
    max_request_body_bytes: int = field(
        default_factory=lambda: _int_env("MAX_REQUEST_BODY_BYTES", 1_048_576)
    )

    # AI provider. "ollama" talks directly to a local Ollama server;
    # "abap-agent" delegates to the user's local streaming RAG service;
    # "openai" uses the hosted Responses API and additionally requires the
    # explicit ALLOW_EXTERNAL_PROVIDERS opt-in below.
    llm_provider: str = field(
        default_factory=lambda: os.environ.get("LLM_PROVIDER", "ollama").lower()
    )

    # Ollama (local by default).
    ollama_base_url: str = field(
        default_factory=lambda: os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434")
    )
    ollama_model: str = field(
        default_factory=lambda: os.environ.get("OLLAMA_MODEL", "gemma4:e4b")
    )

    # Optional local ABAP Expert RAG service. This provider retains the
    # user's Chroma/PDF/Word retrieval pipeline instead of bypassing it and
    # calling Ollama directly.
    abap_agent_base_url: str = field(
        default_factory=lambda: os.environ.get("ABAP_AGENT_BASE_URL", "http://localhost:8000")
    )
    abap_agent_model: str = field(
        default_factory=lambda: os.environ.get("ABAP_AGENT_MODEL", "abap-expert")
    )

    # Hosted OpenAI Responses API. The API key is server-side only and is
    # never sent to or stored by the Eclipse plug-in.
    openai_base_url: str = field(
        default_factory=lambda: os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1")
    )
    openai_api_key: str = field(
        default_factory=lambda: os.environ.get("OPENAI_API_KEY", "")
    )
    openai_model: str = field(
        default_factory=lambda: os.environ.get("OPENAI_MODEL", "gpt-5.6")
    )

    # External providers are OFF by default; enabling requires an explicit
    # opt-in AND redaction stays active.
    allow_external_providers: bool = field(
        default_factory=lambda: os.environ.get("ALLOW_EXTERNAL_PROVIDERS", "false").lower()
        == "true"
    )
    redaction_enabled: bool = field(
        default_factory=lambda: os.environ.get("REDACTION_ENABLED", "true").lower() != "false"
    )

    # Local RAG knowledge base (optional; empty path disables it).
    knowledge_index_path: str = field(
        default_factory=lambda: os.environ.get("KNOWLEDGE_INDEX_PATH", "")
    )
    bundled_knowledge_path: str = field(
        default_factory=lambda: os.environ.get("BUNDLED_KNOWLEDGE_PATH", "")
    )
    embedding_model: str = field(
        default_factory=lambda: os.environ.get("EMBEDDING_MODEL", "nomic-embed-text")
    )

    # Deterministic analyzer (analyzer-core CLI jar).
    analyzer_jar: str = field(
        default_factory=lambda: os.environ.get("ANALYZER_JAR", "")
    )
    java_bin: str = field(default_factory=lambda: os.environ.get("JAVA_BIN", "java"))
    rules_config: str = field(default_factory=lambda: os.environ.get("RULES_CONFIG", ""))

    # Limits (all configurable).
    max_source_length: int = field(default_factory=lambda: _int_env("MAX_SOURCE_LENGTH", 200_000))
    request_timeout_seconds: int = field(default_factory=lambda: _int_env("REQUEST_TIMEOUT_SECONDS", 120))
    ai_timeout_seconds: int = field(default_factory=lambda: _int_env("AI_TIMEOUT_SECONDS", 60))
    max_findings: int = field(default_factory=lambda: _int_env("MAX_FINDINGS", 200))
    max_tokens: int = field(default_factory=lambda: _int_env("MAX_TOKENS", 2048))
    max_chat_context_length: int = field(
        default_factory=lambda: _int_env("MAX_CHAT_CONTEXT_LENGTH", 4000)
    )


settings = Settings()
