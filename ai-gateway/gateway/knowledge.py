"""Knowledge provider abstraction (local RAG).

The gateway can enrich AI prompts with retrieved knowledge (rule docs, SAP
guidelines, team conventions). Two providers ship:

* ``NoOpKnowledgeProvider`` — default, returns nothing, fully offline.
* ``LocalVectorKnowledgeProvider`` — local vector store backed by a JSON
  index of pre-computed Ollama embeddings (see
  ``scripts/build_knowledge_index.py``). Retrieval embeds the query via the
  local Ollama server and ranks snippets by cosine similarity, with an exact
  rule-ID match boost. If embeddings are unavailable it degrades to pure
  rule-ID keyword matching; if the index is missing or corrupt it returns
  nothing. Everything stays local and query text is never logged.
"""

from __future__ import annotations

import json
import math
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Protocol, runtime_checkable

import httpx

from .config import settings


@dataclass
class KnowledgeSnippet:
    source: str
    content: str
    score: float = 0.0


@runtime_checkable
class KnowledgeProvider(Protocol):
    """Retrieval interface. Implementations must never log query text."""

    def retrieve(self, query: str, limit: int = 3) -> list[KnowledgeSnippet]:
        """Return up to `limit` snippets relevant to the query."""
        ...


class NoOpKnowledgeProvider:
    """Default provider: returns nothing, keeps the gateway fully offline."""

    def retrieve(self, query: str, limit: int = 3) -> list[KnowledgeSnippet]:
        return []


_WORD_RE = re.compile(r"[A-Z0-9_]{3,}", re.IGNORECASE)
_SUPPORTED_KNOWLEDGE_SUFFIXES = {".md", ".txt", ".yaml", ".yml", ".abap"}


class BundledKnowledgeProvider:
    """Dependency-free lexical retrieval over documentation shipped in the image.

    Files are read once and split into bounded passages. Retrieval is local to
    the Guardian service: no document is uploaded to a separate vector store.
    """

    def __init__(self, root_path: str) -> None:
        self.root_path = Path(root_path)
        self._snippets: list[KnowledgeSnippet] | None = None

    @staticmethod
    def _tokens(text: str) -> set[str]:
        return {token.upper() for token in _WORD_RE.findall(text)}

    def _load(self) -> list[KnowledgeSnippet]:
        if self._snippets is not None:
            return self._snippets
        snippets: list[KnowledgeSnippet] = []
        if not self.root_path.is_dir():
            self._snippets = snippets
            return snippets
        for path in sorted(self.root_path.rglob("*")):
            if not path.is_file() or path.suffix.lower() not in _SUPPORTED_KNOWLEDGE_SUFFIXES:
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except (OSError, UnicodeError):
                continue
            source = path.relative_to(self.root_path).as_posix()
            paragraphs = [part.strip() for part in re.split(r"\n\s*\n", text) if part.strip()]
            current: list[str] = []
            size = 0
            for paragraph in paragraphs:
                if current and size + len(paragraph) > 1800:
                    snippets.append(KnowledgeSnippet(source=source, content="\n\n".join(current)))
                    current, size = [], 0
                current.append(paragraph[:1800])
                size += len(paragraph)
            if current:
                snippets.append(KnowledgeSnippet(source=source, content="\n\n".join(current)))
        self._snippets = snippets
        return snippets

    def retrieve(self, query: str, limit: int = 3) -> list[KnowledgeSnippet]:
        if limit <= 0:
            return []
        query_tokens = self._tokens(query)
        query_rule_ids = set(_RULE_ID_RE.findall(query.upper()))
        if not query_tokens:
            return []
        scored: list[KnowledgeSnippet] = []
        for snippet in self._load():
            content_tokens = self._tokens(snippet.content)
            overlap = query_tokens.intersection(content_tokens)
            score = len(overlap) / max(1.0, math.sqrt(len(query_tokens) * len(content_tokens)))
            if query_rule_ids.intersection(set(_RULE_ID_RE.findall(snippet.content.upper()))):
                score += _RULE_ID_BOOST
            if score > 0.0:
                scored.append(KnowledgeSnippet(snippet.source, snippet.content, round(score, 4)))
        scored.sort(key=lambda item: item.score, reverse=True)
        return scored[:limit]


_RULE_ID_RE = re.compile(r"\b(?:PERF|SEC|PRIV|POL)_[A-Z0-9_]+\b")

# Exact rule-ID matches are strong signals; they outrank any cosine score
# (which lives in [-1, 1]) so curated per-rule guidance always surfaces.
_RULE_ID_BOOST = 2.0


def _cosine(a: list[float], b: list[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    if na == 0.0 or nb == 0.0:
        return 0.0
    return dot / (na * nb)


@dataclass
class _IndexEntry:
    source: str
    content: str
    rule_ids: list[str] = field(default_factory=list)
    embedding: list[float] = field(default_factory=list)


class LocalVectorKnowledgeProvider:
    """Local vector store over a JSON index of embedded knowledge snippets.

    Index format (produced by ``scripts/build_knowledge_index.py``)::

        {
          "embeddingModel": "nomic-embed-text",
          "snippets": [
            {"source": "...", "content": "...",
             "ruleIds": ["PERF_SELECT_IN_LOOP"], "embedding": [...]}
          ]
        }

    Failure behaviour is deliberately soft: a missing/corrupt index or an
    unreachable embedding endpoint never raises — retrieval falls back to
    rule-ID keyword matching or returns an empty list, so AI enhancement
    keeps working exactly as it does without RAG.
    """

    def __init__(self, index_path: str) -> None:
        self.index_path = index_path
        self._entries: list[_IndexEntry] | None = None
        self._embedding_model: str = settings.embedding_model

    # -- index loading -----------------------------------------------------

    def _load(self) -> list[_IndexEntry]:
        if self._entries is not None:
            return self._entries
        entries: list[_IndexEntry] = []
        try:
            with open(self.index_path, encoding="utf-8") as fh:
                data = json.load(fh)
            model = data.get("embeddingModel")
            if isinstance(model, str) and model:
                self._embedding_model = model
            for raw in data.get("snippets", []):
                content = raw.get("content")
                if not isinstance(content, str) or not content.strip():
                    continue
                embedding = raw.get("embedding") or []
                if not isinstance(embedding, list):
                    embedding = []
                entries.append(
                    _IndexEntry(
                        source=str(raw.get("source", "unknown")),
                        content=content,
                        rule_ids=[str(r) for r in raw.get("ruleIds", [])],
                        embedding=[float(x) for x in embedding],
                    )
                )
        except (OSError, ValueError, TypeError):
            entries = []
        self._entries = entries
        return entries

    # -- query embedding ---------------------------------------------------

    def _embed_query(self, query: str) -> list[float] | None:
        """Embed via local Ollama; None on any failure (never logs query)."""
        try:
            with httpx.Client(
                base_url=settings.ollama_base_url, timeout=10
            ) as client:
                resp = client.post(
                    "/api/embeddings",
                    json={"model": self._embedding_model, "prompt": query},
                )
                resp.raise_for_status()
                embedding = resp.json().get("embedding")
        except (httpx.HTTPError, ValueError):
            return None
        if not isinstance(embedding, list) or not embedding:
            return None
        try:
            return [float(x) for x in embedding]
        except (TypeError, ValueError):
            return None

    # -- retrieval ---------------------------------------------------------

    def retrieve(self, query: str, limit: int = 3) -> list[KnowledgeSnippet]:
        entries = self._load()
        if not entries or limit <= 0:
            return []

        query_rule_ids = set(_RULE_ID_RE.findall(query))
        query_embedding = self._embed_query(query)

        scored: list[tuple[float, _IndexEntry]] = []
        for entry in entries:
            score = 0.0
            if query_embedding and entry.embedding:
                score = _cosine(query_embedding, entry.embedding)
            if query_rule_ids and query_rule_ids.intersection(entry.rule_ids):
                score += _RULE_ID_BOOST
            if score > 0.0:
                scored.append((score, entry))

        scored.sort(key=lambda pair: pair[0], reverse=True)
        return [
            KnowledgeSnippet(source=e.source, content=e.content, score=round(s, 4))
            for s, e in scored[:limit]
        ]


def get_default_provider() -> KnowledgeProvider:
    """Prefer configured vector knowledge, then bundled repository knowledge."""
    index_path = settings.knowledge_index_path
    if index_path and os.path.isfile(index_path):
        return LocalVectorKnowledgeProvider(index_path)
    bundled_path = settings.bundled_knowledge_path
    if bundled_path and os.path.isdir(bundled_path):
        return BundledKnowledgeProvider(bundled_path)
    return NoOpKnowledgeProvider()
