"""Authentication and lightweight abuse protection for the public API."""

from __future__ import annotations

import hashlib
import hmac
import threading
import time
from dataclasses import dataclass

from fastapi import Request
from fastapi.responses import JSONResponse

from .config import settings


@dataclass
class _Window:
    started: float
    count: int


_windows: dict[str, _Window] = {}
_window_lock = threading.Lock()


def _bearer_token(request: Request) -> str:
    authorization = request.headers.get("Authorization", "").strip()
    if authorization.lower().startswith("bearer "):
        return authorization[7:].strip()
    return ""


def _identity(request: Request, token: str) -> str:
    # Hash the token before using it as an in-memory key. Neither the token nor
    # ABAP content is written to logs or persistent storage.
    token_hash = hashlib.sha256(token.encode("utf-8")).hexdigest()[:16]
    # The production gateway is reachable only through the Caddy container,
    # so its first X-Forwarded-For value identifies the original client. Local
    # development falls back to Starlette's socket peer.
    forwarded = request.headers.get("X-Forwarded-For", "").split(",", 1)[0].strip()
    client = forwarded or (request.client.host if request.client else "unknown")
    return f"{client}:{token_hash}"


def _within_rate_limit(identity: str) -> bool:
    limit = settings.rate_limit_per_minute
    if limit <= 0:
        return True
    now = time.monotonic()
    with _window_lock:
        window = _windows.get(identity)
        if window is None or now - window.started >= 60:
            _windows[identity] = _Window(started=now, count=1)
            # Bound memory if many transient client addresses hit the service.
            if len(_windows) > 10_000:
                cutoff = now - 120
                for key in [k for k, value in _windows.items() if value.started < cutoff]:
                    _windows.pop(key, None)
            return True
        if window.count >= limit:
            return False
        window.count += 1
        return True


def enforce_api_security(request: Request) -> JSONResponse | None:
    """Return an error response for a rejected API request, otherwise None."""
    if not request.url.path.startswith("/api/v1/"):
        return None

    content_length = request.headers.get("Content-Length")
    if content_length:
        try:
            if int(content_length) > settings.max_request_body_bytes:
                return JSONResponse(
                    status_code=413,
                    content={"detail": "Request body exceeds the configured limit"},
                )
        except ValueError:
            return JSONResponse(status_code=400, content={"detail": "Invalid Content-Length"})

    configured_token = settings.api_token
    if settings.require_api_auth and not configured_token:
        return JSONResponse(
            status_code=503,
            content={"detail": "API authentication is required but not configured"},
        )
    if not configured_token:
        return None

    supplied_token = _bearer_token(request)
    if not supplied_token or not hmac.compare_digest(supplied_token, configured_token):
        return JSONResponse(
            status_code=401,
            content={"detail": "Missing or invalid API token"},
            headers={"WWW-Authenticate": "Bearer"},
        )
    if not _within_rate_limit(_identity(request, supplied_token)):
        return JSONResponse(
            status_code=429,
            content={"detail": "Request rate limit exceeded"},
            headers={"Retry-After": "60"},
        )
    return None


def clear_rate_limits_for_tests() -> None:
    with _window_lock:
        _windows.clear()
