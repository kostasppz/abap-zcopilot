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

from . import __version__, analyzer, llm_client
from .config import settings
from .enhancement import enhance_findings
from .knowledge import get_default_provider
from .redaction import redact
from .schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    ChatRequest,
    ChatResponse,
    ExplainRequest,
    ExplainResponse,
    Finding,
    HealthResponse,
    ModelsResponse,
    SuggestFixRequest,
    SuggestFixResponse,
)
from .security import enforce_api_security

# Deliberately quiet: uvicorn access logs would contain only paths, never
# bodies, but we also avoid app-level logging of any request content.
logger = logging.getLogger("abap_guardian.gateway")

_CHAT_SYSTEM = (
    "You are ABAP Guardian Copilot, a specialist assistant for SAP ABAP code review. "
    "Answer only questions related to ABAP, the active code, static-analysis findings, "
    "performance, security, privacy and the supplied project knowledge. Distinguish "
    "facts from suggestions. Never claim that suggested code is safe without human "
    "review. Do not invent repository rules or source locations."
)

app = FastAPI(
    title="ABAP Guardian AI Gateway",
    version=__version__,
    description="Local-first analysis gateway. Deterministic rules first; AI optional.",
)


@app.middleware("http")
async def secure_public_api(request, call_next):
    rejection = enforce_api_security(request)
    if rejection is not None:
        rejection.headers["Cache-Control"] = "no-store"
        return rejection
    response = await call_next(request)
    if request.url.path.startswith("/api/v1/"):
        response.headers["Cache-Control"] = "no-store"
    return response


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    llm_ok = await llm_client.is_available()
    analyzer_ok = analyzer.analyzer_available()
    auth_configured = bool(settings.api_token)
    status = "ok" if analyzer_ok and (not settings.require_api_auth or auth_configured) else "degraded"
    return HealthResponse(
        status=status,
        llmAvailable=llm_ok,
        llmProvider=llm_client.provider_name(),
        ollamaAvailable=llm_ok and llm_client.provider_name() == "ollama",
        analyzerAvailable=analyzer_ok,
        authenticationRequired=settings.require_api_auth or auth_configured,
        authenticationConfigured=auth_configured,
        version=__version__,
    )


@app.get("/api/v1/models", response_model=ModelsResponse)
async def models() -> ModelsResponse:
    names = await llm_client.list_models()
    return ModelsResponse(models=names, default=llm_client.model_name())


@app.post("/api/v1/chat", response_model=ChatResponse)
async def chat(request: ChatRequest) -> ChatResponse:
    if not await llm_client.is_available():
        raise HTTPException(status_code=503, detail="AI model unavailable")
    source = request.source[: settings.max_chat_context_length]
    query = " ".join(part for part in (request.question, request.selection, source[:2000]) if part)
    snippets = get_default_provider().retrieve(query, limit=4)

    prompt_parts = [
        f"ABAP object: {request.objectName} ({request.objectType})",
        f"Developer question:\n{request.question}",
    ]
    if request.history:
        transcript = "\n".join(
            f"{message.role}: {message.content}" for message in request.history[-8:]
        )
        prompt_parts.append("Recent conversation:\n" + transcript)
    if snippets:
        prompt_parts.append(
            "Relevant repository knowledge:\n"
            + "\n\n".join(f"[{item.source}]\n{item.content}" for item in snippets)
        )
    if request.selection:
        prompt_parts.append("Selected ABAP code:\n" + request.selection)
    elif source:
        prompt_parts.append("Active ABAP source:\n" + source)
    prompt = "\n\n".join(prompt_parts)
    if settings.redaction_enabled:
        prompt = redact(prompt)
    try:
        answer = await llm_client.generate_text(prompt, system=_CHAT_SYSTEM)
    except llm_client.LlmError as exc:
        raise HTTPException(status_code=503, detail="AI model unavailable") from exc
    return ChatResponse(
        answer=answer,
        model=llm_client.model_name(),
        knowledgeReferences=list(dict.fromkeys(item.source for item in snippets)),
        contextIncluded=bool(request.selection or source),
    )


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
    if request.useAi and findings and await llm_client.is_available():
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
        model=llm_client.model_name() if ai_enhanced else None,
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
        text = await llm_client.generate_text(prompt)
    except llm_client.LlmError as exc:
        raise HTTPException(status_code=503, detail="AI model unavailable") from exc
    return ExplainResponse(explanation=text, model=llm_client.model_name())


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
        raw = await llm_client.generate_json(prompt)
    except llm_client.LlmError as exc:
        raise HTTPException(status_code=503, detail="AI model unavailable") from exc
    if not isinstance(raw, dict) or "suggestedCode" not in raw:
        raise HTTPException(status_code=502, detail="AI reply failed schema validation")
    return SuggestFixResponse(
        suggestedCode=str(raw.get("suggestedCode", "")),
        caveats=str(raw.get("caveats", "")),
        model=llm_client.model_name(),
        requiresHumanReview=True,
    )
