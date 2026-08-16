from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
RUNPOD_DIR = REPOSITORY_ROOT / "deploy" / "runpod"


def read_runpod_file(name: str) -> str:
    return (RUNPOD_DIR / name).read_text(encoding="utf-8")


def test_agent_remains_loopback_only() -> None:
    start_agent = read_runpod_file("start-agent.sh")

    assert "--host 127.0.0.1" in start_agent
    assert "--port 8000" in start_agent


def test_nginx_protects_the_public_agent_proxy() -> None:
    nginx = read_runpod_file("nginx-agent-web.conf")

    assert "listen 8002 default_server;" in nginx
    assert "auth_basic " in nginx
    assert "auth_basic_user_file " in nginx
    assert "proxy_pass http://127.0.0.1:8000;" in nginx
    assert 'proxy_set_header Authorization "";' in nginx
    assert "listen 8001" not in nginx


def test_runpod_base_creates_authentication_before_nginx_starts() -> None:
    start_base = read_runpod_file("start-runpod-base.sh")
    supervisor = read_runpod_file("supervisord.conf")

    assert "AGENT_WEB_PASSWORD" in start_base
    assert "htpasswd -ciB" in start_base
    assert "AGENT_WEB_PASSWORD must contain at least 24 characters" in start_base
    assert 'install -d -o root -g "$nginx_auth_group" -m 0750' in start_base
    assert 'chown root:"$nginx_auth_group"' in start_base
    assert "chmod 0640" in start_base
    assert "command=/opt/runpod/start-runpod-base.sh" in supervisor


def test_container_exposes_only_the_two_application_http_ports() -> None:
    dockerfile = read_runpod_file("Dockerfile")

    assert "rm -f /etc/nginx/sites-enabled/* /etc/nginx/conf.d/*.conf" in dockerfile
    assert "s/listen 8001;/listen 127.0.0.1:18001;/" in dockerfile
    assert "include /etc/nginx/conf.d/*.conf;" in dockerfile
    assert "! grep -Eq 'listen[[:space:]]+8001;'" in dockerfile
    assert "EXPOSE 8001 8002" in dockerfile
