"""Gateway configuration.

Everything is local by default. External AI providers are disabled unless
explicitly enabled, and even then requests pass through the redaction layer.
Source code is never stored or logged.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field


def _int_env(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, default))
    except ValueError:
        return default


@dataclass
class Settings:
    # Ollama (local by default).
    ollama_base_url: str = field(
        default_factory=lambda: os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434")
    )
    ollama_model: str = field(
        default_factory=lambda: os.environ.get("OLLAMA_MODEL", "gemma4:e4b")
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


settings = Settings()
