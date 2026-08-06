"""AI enhancement of deterministic findings.

Hard invariants enforced here:
  * Deterministic analysis always runs first; AI is optional and additive.
  * The AI can improve explanations/recommendations/suggested code, but it
    can NEVER add findings, remove findings, or alter line/column numbers.
  * The AI reply must validate against the AiEnhancement schema; anything
    else is dropped silently (the deterministic finding stands unmodified).
"""

from __future__ import annotations

from pydantic import ValidationError

from . import ollama_client
from .config import settings
from .knowledge import KnowledgeProvider, get_default_provider
from .redaction import redact
from .schemas import AiEnhancement, Finding

_SYSTEM = (
    "You are an ABAP code review assistant. You receive findings from a "
    "deterministic static analyzer. Improve the explanation, recommendation "
    "and suggested code. Reply with a JSON array of objects with keys "
    "ruleId, explanation, recommendation, suggestedCode. Never invent line "
    "numbers, new findings, or claim certainty the analyzer did not have."
)


def _prompt(findings: list[Finding], snippet: str, knowledge: str) -> str:
    lines = ["Findings:"]
    for f in findings:
        lines.append(
            f"- {f.ruleId} ({f.severity}) line {f.startLine}: {f.title}"
        )
    if knowledge:
        lines.append("\nRelevant knowledge:\n" + knowledge)
    if snippet:
        lines.append("\nCode snippet:\n" + snippet)
    return "\n".join(lines)


async def enhance_findings(
    findings: list[Finding],
    snippet: str,
    provider: KnowledgeProvider | None = None,
) -> list[Finding]:
    """Return findings with AI-improved texts. Line numbers are immutable."""
    if not findings:
        return findings
    provider = provider or get_default_provider()
    knowledge_snippets = provider.retrieve(
        ", ".join(f.ruleId for f in findings), limit=3
    )
    knowledge = "\n".join(s.content for s in knowledge_snippets)
    prompt = _prompt(findings, snippet, knowledge)
    if settings.redaction_enabled:
        prompt = redact(prompt)
    try:
        raw = await ollama_client.generate_json(prompt, system=_SYSTEM)
    except ollama_client.OllamaError:
        return findings

    if not isinstance(raw, list):
        raw = [raw] if isinstance(raw, dict) else []
    enhancements: dict[str, AiEnhancement] = {}
    for item in raw:
        try:
            enh = AiEnhancement.model_validate(item)
        except ValidationError:
            continue
        enhancements[enh.ruleId] = enh

    result: list[Finding] = []
    for f in findings:
        enh = enhancements.get(f.ruleId)
        if enh is None:
            result.append(f)
            continue
        # Copy: only text fields may change. Positions are taken from the
        # original deterministic finding, unconditionally.
        result.append(
            f.model_copy(
                update={
                    "explanation": enh.explanation or f.explanation,
                    "recommendation": enh.recommendation or f.recommendation,
                    "suggestedCode": enh.suggestedCode or f.suggestedCode,
                }
            )
        )
    return result
