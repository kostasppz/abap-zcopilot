from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from gateway.main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


SAMPLE_FINDING = {
    "ruleId": "PERF_SELECT_IN_LOOP",
    "category": "PERFORMANCE",
    "severity": "HIGH",
    "confidence": 0.9,
    "title": "SELECT inside loop",
    "explanation": "A database SELECT runs on every loop iteration.",
    "evidence": "SELECT SINGLE * FROM pa0002 ...",
    "startLine": 3,
    "startColumn": 3,
    "endLine": 6,
    "endColumn": 40,
    "recommendation": "Read the data once before the loop.",
    "suggestedCode": None,
    "requiresHumanReview": False,
    "documentationReferences": [],
}


@pytest.fixture
def sample_finding() -> dict:
    return dict(SAMPLE_FINDING)


@pytest.fixture
def deterministic_result(sample_finding: dict) -> dict:
    return {
        "objectName": "ZTEST",
        "objectType": "PROG",
        "findings": [sample_finding],
        "suppressedFindings": [],
    }
