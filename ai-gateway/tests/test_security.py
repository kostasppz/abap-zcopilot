from __future__ import annotations

from unittest.mock import patch

from gateway.config import settings
from gateway.security import clear_rate_limits_for_tests


def test_health_is_public_and_reports_authentication(client):
    with patch.object(settings, "api_token", "test-token"), \
         patch.object(settings, "require_api_auth", True), \
         patch("gateway.llm_client.is_available", return_value=True), \
         patch("gateway.analyzer.analyzer_available", return_value=True):
        response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["authenticationRequired"] is True
    assert response.json()["authenticationConfigured"] is True


def test_api_rejects_missing_or_wrong_token(client):
    with patch.object(settings, "api_token", "correct-token"), \
         patch.object(settings, "require_api_auth", True):
        missing = client.get("/api/v1/models")
        wrong = client.get(
            "/api/v1/models", headers={"Authorization": "Bearer wrong-token"}
        )
    assert missing.status_code == 401
    assert wrong.status_code == 401
    assert missing.headers["www-authenticate"] == "Bearer"
    assert missing.headers["cache-control"] == "no-store"


def test_api_accepts_valid_bearer_token(client):
    with patch.object(settings, "api_token", "correct-token"), \
         patch.object(settings, "require_api_auth", True), \
         patch("gateway.llm_client.list_models", return_value=["abap-expert"]):
        response = client.get(
            "/api/v1/models",
            headers={"Authorization": "Bearer correct-token"},
        )
    assert response.status_code == 200
    assert response.json()["models"] == ["abap-expert"]
    assert response.headers["cache-control"] == "no-store"


def test_required_auth_without_secret_fails_closed(client):
    with patch.object(settings, "api_token", ""), \
         patch.object(settings, "require_api_auth", True):
        response = client.get("/api/v1/models")
    assert response.status_code == 503


def test_oversized_request_is_rejected_before_authentication(client):
    with patch.object(settings, "max_request_body_bytes", 10):
        response = client.post(
            "/api/v1/chat",
            content=b'{"question":"this is intentionally too large"}',
            headers={"Content-Type": "application/json"},
        )
    assert response.status_code == 413


def test_rate_limit_rejects_excess_requests(client):
    clear_rate_limits_for_tests()
    with patch.object(settings, "api_token", "correct-token"), \
         patch.object(settings, "require_api_auth", True), \
         patch.object(settings, "rate_limit_per_minute", 1), \
         patch("gateway.llm_client.list_models", return_value=[]):
        first = client.get(
            "/api/v1/models", headers={"Authorization": "Bearer correct-token"}
        )
        second = client.get(
            "/api/v1/models", headers={"Authorization": "Bearer correct-token"}
        )
    assert first.status_code == 200
    assert second.status_code == 429
