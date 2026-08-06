"""Optional redaction layer for prompts leaving the local machine.

Applied before any content is sent to an external (non-local) provider.
Local Ollama traffic can also be redacted if desired.
"""

from __future__ import annotations

import re

_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    # Order matters: specific identifiers before generic ones.
    (re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.IGNORECASE), "<EMAIL>"),
    (re.compile(r"\b[A-Z]{2}\d{2}[A-Z0-9]{11,30}\b"), "<IBAN>"),
    (re.compile(r"\b\d{8}\b"), "<PERNR>"),
    (re.compile(r"\+?\d[\d\s/-]{7,}\d"), "<PHONE>"),
    (re.compile(r"(password|passwort|pwd)(\s*=\s*)'[^']*'", re.IGNORECASE), r"\1\2'<REDACTED>'"),
    (re.compile(r"(bearer\s+)[a-z0-9._-]+", re.IGNORECASE), r"\1<TOKEN>"),
]


def redact(text: str) -> str:
    """Replace likely personal data and credentials with placeholders."""
    for pattern, replacement in _PATTERNS:
        text = pattern.sub(replacement, text)
    return text
