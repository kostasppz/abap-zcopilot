"""Pydantic wire schemas. AI output MUST validate against these models."""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator

Severity = Literal["INFO", "LOW", "MEDIUM", "HIGH", "CRITICAL"]
Category = Literal[
    "PERFORMANCE",
    "SECURITY",
    "S4HANA",
    "CLEAN_CODE",
    "PRIVACY",
    "POLICY",
    "MAINTAINABILITY",
]


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
    categories: list[Category] = Field(default_factory=list)

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
    suggestedCode: str = Field(min_length=1, max_length=20_000)
    caveats: str = ""
    model: str
    requiresHumanReview: bool = True


class ChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class ChatRequest(BaseModel):
    question: str = Field(min_length=1, max_length=8000)
    objectName: str = Field(default="UNKNOWN", max_length=255)
    objectType: str = Field(default="PROG", max_length=32)
    source: str = ""
    selection: str = Field(default="", max_length=4000)
    history: list[ChatMessage] = Field(default_factory=list, max_length=12)

    @field_validator("question")
    @classmethod
    def question_not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("question must not be blank")
        return value


class ChatResponse(BaseModel):
    answer: str
    model: str
    knowledgeReferences: list[str] = Field(default_factory=list)
    contextIncluded: bool = False


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
    llmAvailable: bool
    llmProvider: str
    # Kept for backward compatibility with existing local installations.
    ollamaAvailable: bool
    analyzerAvailable: bool
    authenticationRequired: bool = False
    authenticationConfigured: bool = False
    version: str
