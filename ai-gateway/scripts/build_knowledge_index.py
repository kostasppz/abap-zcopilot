#!/usr/bin/env python3
"""Build the local RAG knowledge index for the AI gateway.

Reads curated markdown knowledge files (rule docs, SAP best-practice notes),
splits them into snippets, embeds each snippet via the local Ollama server,
and writes a JSON index consumed by ``LocalVectorKnowledgeProvider``.

Usage (from ai-gateway/)::

    .venv/bin/python scripts/build_knowledge_index.py \
        --out knowledge_index.json \
        ../docs/rules.md ../docs/knowledge/*.md

Everything stays local: only the local Ollama embeddings endpoint is called.
Snippets are chunked per markdown section (## / ### headings) and per table
row for rule reference tables; rule IDs found in a chunk are stored so
retrieval can boost exact rule matches even without embeddings.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import httpx

RULE_ID_RE = re.compile(r"\b(?:PERF|SEC|PRIV|POL)_[A-Z0-9_]+\b")


def chunk_markdown(path: Path) -> list[dict]:
    """Split a markdown file into snippet dicts (source, content, ruleIds)."""
    text = path.read_text(encoding="utf-8")
    chunks: list[dict] = []
    section_title = path.stem
    buf: list[str] = []

    def flush() -> None:
        content = "\n".join(buf).strip()
        buf.clear()
        if len(content) < 40:  # skip trivial fragments
            return
        chunks.append(
            {
                "source": f"{path.name}#{section_title}",
                "content": content,
                "ruleIds": sorted(set(RULE_ID_RE.findall(content))),
            }
        )

    for line in text.splitlines():
        heading = re.match(r"^(#{1,3})\s+(.*)", line)
        if heading:
            flush()
            section_title = heading.group(2).strip()
            continue
        # Rule reference table rows become standalone snippets keyed by rule.
        row = re.match(r"^\|\s*`((?:PERF|SEC|PRIV|POL)_[A-Z0-9_]+)`\s*\|(.*)\|\s*$", line)
        if row:
            cells = [c.strip() for c in row.group(2).split("|")]
            desc = cells[-1] if cells else ""
            chunks.append(
                {
                    "source": f"{path.name}#{section_title}",
                    "content": f"{row.group(1)}: {desc}",
                    "ruleIds": [row.group(1)],
                }
            )
            continue
        buf.append(line)
    flush()
    return chunks


def embed(client: httpx.Client, model: str, text: str) -> list[float]:
    resp = client.post("/api/embeddings", json={"model": model, "prompt": text})
    resp.raise_for_status()
    embedding = resp.json().get("embedding") or []
    if not embedding:
        raise RuntimeError("empty embedding returned")
    return embedding


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", help="Markdown knowledge files")
    parser.add_argument("--out", default="knowledge_index.json")
    parser.add_argument("--ollama-url", default="http://localhost:11434")
    parser.add_argument("--model", default="nomic-embed-text")
    parser.add_argument(
        "--no-embeddings",
        action="store_true",
        help="Build a keyword-only index (retrieval falls back to rule-ID matching)",
    )
    args = parser.parse_args()

    snippets: list[dict] = []
    for name in args.inputs:
        path = Path(name)
        if not path.is_file():
            print(f"skipping missing file: {path}", file=sys.stderr)
            continue
        snippets.extend(chunk_markdown(path))

    if not snippets:
        print("no snippets extracted; nothing to index", file=sys.stderr)
        return 1

    if not args.no_embeddings:
        with httpx.Client(base_url=args.ollama_url, timeout=60) as client:
            for i, snippet in enumerate(snippets, 1):
                snippet["embedding"] = embed(client, args.model, snippet["content"])
                print(f"embedded {i}/{len(snippets)}", file=sys.stderr)

    index = {"embeddingModel": args.model, "snippets": snippets}
    Path(args.out).write_text(json.dumps(index), encoding="utf-8")
    print(f"wrote {len(snippets)} snippets to {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
