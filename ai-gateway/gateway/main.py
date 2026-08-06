"""ABAP Guardian AI gateway — FastAPI application.

Privacy invariants:
  * No ABAP source is ever stored or logged (logging of request bodies is
    disabled; the analyzer bridge pipes source via stdin only).
  * Fully local by default (Ollama at localhost); external providers are
    disabled unless explicitly opted in, and redaction stays active.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI, HTTPException

from . import __version__, analyzer, ollama_client
from .config import settings
from .enhancement import enhance_findings
from .redaction import redact
from .schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    ExplainRequest,
    ExplainResponse,
    Finding,
    HealthResponse,
    ModelsResponse,
    SuggestFixRequest,
    SuggestFixResponse,
)

# Deliberately quiet: uvicorn access logs would contain only paths, never
# bodies, but we also avoid app-level logging of any request content.
logger = logging.getLogger("abap_guardian.gateway")

app = FastAPI(
    title="ABAP Guardian AI Gateway",
    version=__version__,
    description="Local-first analysis gateway. Deterministic rules first; AI optional.",
)


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    ollama_ok = await ollama_client.is_available()
    analyzer_ok = analyzer.analyzer_available()
    status = "ok" if analyzer_ok else "degraded"
    return HealthResponse(
        status=status,
        ollamaAvailable=ollama_ok,
        analyzerAvailable=analyzer_ok,
        version=__version__,
    )


@app.get("/api/v1/models", response_model=ModelsResponse)
async def models() -> ModelsResponse:
    try:
        names = await ollama_client.list_models()
    except Exception:  # noqa: BLE001 - degrade gracefully, never leak details
        names = []
    return ModelsResponse(models=names, default=settings.ollama_model)


@app.post("/api/v1/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    if len(request.source) > settings.max_source_length:
        raise HTTPException(
            status_code=413,
            detail=f"source exceeds configured limit of {settings.max_source_length} characters",
        )
    try:
        raw = analyzer.run_deterministic_analysis(
            request.source, request.objectName, request.objectType
        )
    except analyzer.AnalyzerError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    findings = [Finding.model_validate(f) for f in raw.get("findings", [])]
    suppressed = [Finding.model_validate(f) for f in raw.get("suppressedFindings", [])]
    findings = findings[: settings.max_findings]

    ai_enhanced = False
    if request.useAi and findings and await ollama_client.is_available():
        # AI sees at most a bounded snippet, never persists anything, and
        # cannot alter positions (enforced in enhance_findings).
        snippet = request.source[:4000]
        findings = await enhance_findings(findings, snippet)
        ai_enhanced = True

    return AnalyzeResponse(
        objectName=request.objectName,
        objectType=request.objectType,
        findings=findings,
        suppressedFindings=suppressed,
        aiEnhanced=ai_enhanced,
        model=settings.ollama_model if ai_enhanced else None,
    )


@app.post("/api/v1/explain", response_model=ExplainResponse)
async def explain(request: ExplainRequest) -> ExplainResponse:
    prompt = (
        f"Explain this ABAP static analysis finding to a developer.\n"
        f"Rule: {request.finding.ruleId}\nTitle: {request.finding.title}\n"
        f"Explanation so far: {request.finding.explanation}\n"
    )
    if request.sourceSnippet:
        prompt += f"Code:\n{request.sourceSnippet}\n"
    if settings.redaction_enabled:
        prompt = redact(prompt)
    try:
        text = await ollama_client.generate_text(prompt)
    except ollama_client.OllamaError as exc:
        raise HTTPException(status_code=503, detail="AI model unavailable") from exc
    return ExplainResponse(explanation=text, model=settings.ollama_model)


@app.post("/api/v1/suggest-fix", response_model=SuggestFixResponse)
async def suggest_fix(request: SuggestFixRequest) -> SuggestFixResponse:
    prompt = (
        f"Suggest corrected ABAP code for this finding. Reply as JSON with "
        f'keys "suggestedCode" and "caveats".\n'
        f"Rule: {request.finding.ruleId}\nTitle: {request.finding.title}\n"
        f"Recommendation: {request.finding.recommendation}\n"
    )
    if request.sourceSnippet:
        prompt += f"Code:\n{request.sourceSnippet}\n"
    if settings.redaction_enabled:
        prompt = redact(prompt)
    try:
        raw = await ollama_client.generate_json(prompt)
    except ollama_client.OllamaError as exc:
        raise HTTPException(status_code=503, detail="AI model unavailable") from exc
    if not isinstance(raw, dict) or "suggestedCode" not in raw:
        raise HTTPException(status_code=502, detail="AI reply failed schema validation")
    return SuggestFixResponse(
        suggestedCode=str(raw.get("suggestedCode", "")),
        caveats=str(raw.get("caveats", "")),
        model=settings.ollama_model,
        requiresHumanReview=True,
    )
