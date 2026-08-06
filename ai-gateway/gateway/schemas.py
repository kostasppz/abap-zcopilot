"""Pydantic wire schemas. AI output MUST validate against these models."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator

Severity = Literal["INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL"]
Category = Literal["PERFORMANCE", "SECURITY", "PRIVACY", "POLICY"]


class Finding(BaseModel):
    ruleId: str
    category: Category
    severity: Severity
    confidence: float = Field(ge=0.0, le=1.0)
    title: str
    explanation: str
    evidence: str = ""
    startLine: int = Field(ge=1)
    startColumn: int = Field(ge=1)
    endLine: int = Field(ge=1)
    endColumn: int = Field(ge=1)
    recommendation: str = ""
    suggestedCode: str | None = None
    requiresHumanReview: bool = False
    documentationReferences: list[str] = Field(default_factory=list)


class AnalyzeRequest(BaseModel):
    source: str
    objectName: str = "UNKNOWN"
    objectType: str = "PROG"
    useAi: bool = True

    @field_validator("source")
    @classmethod
    def source_not_empty(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("source must not be empty")
        return v


class AnalyzeResponse(BaseModel):
    objectName: str
    objectType: str
    findings: list[Finding]
    suppressedFindings: list[Finding] = Field(default_factory=list)
    aiEnhanced: bool = False
    model: str | None = None


class ExplainRequest(BaseModel):
    finding: Finding
    sourceSnippet: str = Field(default="", max_length=4000)


class ExplainResponse(BaseModel):
    explanation: str
    model: str


class SuggestFixRequest(BaseModel):
    finding: Finding
    sourceSnippet: str = Field(default="", max_length=4000)


class SuggestFixResponse(BaseModel):
    suggestedCode: str
    caveats: str = ""
    model: str
    requiresHumanReview: bool = True


class AiEnhancement(BaseModel):
    """Schema the model's JSON reply must conform to for /analyze enhancement.

    The AI may improve explanations/recommendations but can never invent or
    alter line numbers — those come only from the deterministic engine.
    """

    ruleId: str
    explanation: str | None = None
    recommendation: str | None = None
    suggestedCode: str | None = None


class ModelsResponse(BaseModel):
    models: list[str]
    default: str


class HealthResponse(BaseModel):
    status: Literal["ok", "degraded"]
    ollamaAvailable: bool
    analyzerAvailable: bool
    version: str
