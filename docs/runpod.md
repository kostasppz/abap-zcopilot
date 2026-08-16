# ABAP Guardian on RunPod with Ollama, RAG and Eclipse/ADT

This guide deploys the complete ABAP AI away from the local computer. RunPod
provides the GPU, RAM and persistent storage. The developer computer runs only
Eclipse/ADT and connects to ABAP Guardian through authenticated HTTPS.

The deployment retains:

- the custom Ollama model `abap-expert`;
- the embedding model `embeddinggemma`;
- the existing `agent.py` RAG implementation;
- PDF, Word, ABAP, Markdown and text knowledge files;
- the persistent Chroma vector database;
- ABAP Guardian's deterministic analyzer;
- bearer-token authentication for Eclipse;
- a separately password-protected browser chat;
- redaction, size limits and rate limiting;
- Eclipse Secure Storage for the API token.

No OpenAI API or OpenAI API key is used.

Unlike the AWS EC2 path, this setup does not begin with an EC2 GPU-vCPU quota
increase. You fund the RunPod account and deploy an available GPU. Deployment
still depends on current regional capacity and any account verification shown
by RunPod.

> **Security and compliance:** Eclipse sends ABAP source to the RunPod Pod for
> processing. Before using production or customer source, confirm that the
> selected RunPod cloud tier, European data centre, administrators, retention,
> backups and contractual controls are approved by the organization. Generated
> code still requires SAP syntax checks, activation, ATC and human review.

## 1. Target architecture

```text
Eclipse / ABAP Development Tools
  -> HTTPS + Guardian bearer token
  -> https://<POD_ID>-8001.proxy.runpod.net
  -> ABAP Guardian gateway on port 8001

Browser
  -> HTTPS + Basic Authentication
  -> https://<POD_ID>-8002.proxy.runpod.net
  -> Nginx on port 8002
  -> ABAP Expert RAG service on 127.0.0.1:8000
  -> Ollama on 127.0.0.1:11434
  -> NVIDIA GPU

Persistent network volume mounted at /workspace
  -> Ollama models
  -> knowledge documents
  -> Chroma vector database
  -> logs and backups
```

Guardian port `8001` and the password-protected browser proxy on `8002` are
exposed through RunPod's HTTPS proxy. The ABAP Expert service itself and
Ollama remain bound to loopback. SSH is exposed temporarily for administration
and file transfer.

RunPod's HTTP proxy URL follows the format
`https://POD_ID-INTERNAL_PORT.proxy.runpod.net`. The proxy is HTTPS-only and
publicly reachable, so application authentication remains mandatory. The
proxy also has a 100-second Cloudflare connection limit. This deployment caps
the Guardian AI timeout below that limit. See the official
[RunPod port documentation](https://docs.runpod.io/pods/configuration/expose-ports).

## 2. Why Docker Compose is not used on the Pod

A RunPod Pod is already a container. It is not a conventional Ubuntu VM with a
Docker daemon, and Docker Compose cannot run directly inside it. The equivalent
deployment uses one custom image containing:

- Ollama;
- Java 21 and the deterministic analyzer JAR;
- the Guardian Python gateway;
- the ABAP Agent Python dependencies;
- Supervisor to start and monitor all processes;
- RunPod's normal SSH/startup services.

Private agent source, knowledge, vector data and model weights are not baked
into the image. They remain on the persistent `/workspace` volume. RunPod
documents custom Pod images and templates as its normal deployment model:
[custom templates](https://docs.runpod.io/pods/templates/create-custom-template).

## 3. Prerequisites

You need:

1. A RunPod account with a payment method or prepaid credit.
2. A Docker Hub account and a private repository.
3. Docker Desktop running on Windows.
4. The prepared ABAP Guardian `0.4.0` source tree on Windows.
5. The private local `abap-agent` folder containing:
   - `agent.py`;
   - `Dockerfile` and/or `requirements.txt`;
   - `Modelfile`;
   - `knowledge/`;
   - `templates/`;
   - `static/`;
   - `vector_db/`.
6. Eclipse with ABAP Development Tools.
7. An approved password manager for tokens.

The secure deployment requires ABAP Guardian `0.4.0` or newer. If the Eclipse
Preferences page does not contain an **API token** field, update/publish the
prepared `0.4.0` plug-in before connecting it to RunPod.

Throughout this guide, replace these placeholders:

| Placeholder | Example |
| --- | --- |
| `<GUARDIAN_REPO>` | `C:\Users\Kostas\abap-zcopilot` |
| `<AGENT_PARENT>` | `C:\Users\Kostas` |
| `<DOCKER_USER>` | your Docker Hub username |
| `<POD_ID>` | the ID displayed by RunPod |
| `<POD_IP>` | public IP shown under **Connect** |
| `<SSH_PORT>` | external TCP port mapped to internal port 22 |

Never paste real API tokens, registry tokens, private SSH keys or proprietary
knowledge into GitHub issues, screenshots or chat messages.

## 4. Check the local models before renting a GPU

Run in Windows PowerShell while the local containers are running:

```powershell
docker exec abap-ollama ollama list
docker exec abap-ollama ollama show abap-expert
```

Record the exact chat and embedding model names. This tutorial uses:

```text
CHAT_MODEL=abap-expert
EMBEDDING_MODEL=embeddinggemma
```

Choose GPU memory based on the base model referenced by the `Modelfile`:

| Quantized model class | Practical starting GPU memory |
| --- | ---: |
| 7–8B Q4 | 16 GB |
| 13–14B Q4 | 24 GB |
| 30–32B Q4 | 48 GB |
| 70B | 80 GB or more |

For the current ABAP project, start with one 24 GB GPU such as an NVIDIA L4,
RTX A5000, RTX 3090 or RTX 4090. Prefer **Secure Cloud** in a European data
centre. GPU availability and prices change, so use the price displayed during
deployment rather than assuming a fixed rate.

## 5. Configure RunPod billing safety

1. Sign in to RunPod.
2. Open **Billing**.
3. Add only the amount needed for initial testing or enable carefully limited
   auto-pay.
4. Enable a **Low balance alert**.
5. Record the displayed hourly GPU price before deployment.

When the balance reaches zero, RunPod stops Pods. Persistent storage continues
to incur charges. RunPod documents these behaviors in its
[billing guide](https://docs.runpod.io/accounts-billing/billing).

## 6. Create a dedicated SSH key on Windows

Open PowerShell:

```powershell
New-Item -ItemType Directory -Force -Path "$HOME\.ssh"

ssh-keygen -t ed25519 `
  -f "$HOME\.ssh\abap-guardian-runpod" `
  -C "abap-guardian-runpod"
```

Enter a passphrase when prompted. Display only the public key:

```powershell
Get-Content "$HOME\.ssh\abap-guardian-runpod.pub"
```

In RunPod:

1. Open **Settings → SSH Public Keys**.
2. Add the complete public-key line.
3. Never upload `abap-guardian-runpod` without the `.pub` suffix. That is the
   private key.

RunPod's official SSH instructions distinguish basic SSH from full SSH. SCP
requires a Pod with a public IP and TCP port 22 exposed. See
[RunPod SSH](https://docs.runpod.io/pods/configuration/use-ssh).

## 7. Create a private Docker Hub repository

In Docker Hub:

1. Choose **Create repository**.
2. Name it `abap-guardian-runpod`.
3. Set visibility to **Private**.
4. Under account security, create a Docker Hub access token with permission to
   read and write this repository.
5. Store the token in the password manager.

Log in from PowerShell without putting the token directly into the command:

```powershell
docker login -u <DOCKER_USER>
```

Paste the Docker Hub access token when prompted.

## 8. Add the RunPod image files to ABAP Guardian

Under `<GUARDIAN_REPO>`, create this folder:

```text
deploy/runpod/
```

The finished repository layout is:

```text
abap-zcopilot/
  ai-gateway/
  analyzer-core/
  docs/
  rules/
  deploy/
    runpod/
      Dockerfile
      run.sh
      start-runpod-base.sh
      nginx-agent-web.conf
      start-ollama.sh
      start-agent.sh
      start-guardian.sh
      supervisord.conf
      Dockerfile.dockerignore
```

### 8.1 `deploy/runpod/Dockerfile`

```dockerfile
# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21 AS analyzer-build

WORKDIR /build
COPY pom.xml ./
COPY analyzer-core/pom.xml analyzer-core/pom.xml
COPY analyzer-core/src analyzer-core/src
RUN mvn -B -pl analyzer-core -am clean package -DskipTests

FROM runpod/pytorch:1.0.2-cu1281-torch280-ubuntu2404

ENV DEBIAN_FRONTEND=noninteractive \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

RUN apt-get update && apt-get install -y --no-install-recommends \
        apache2-utils \
        ca-certificates \
        curl \
        openjdk-21-jre-headless \
        python3.12 \
        python3.12-venv \
        supervisor \
        zstd \
    && rm -rf /var/lib/apt/lists/*

# Ollama's files are installed in the image. Model weights remain on /workspace.
RUN curl -fsSL https://ollama.com/download/ollama-linux-amd64.tar.zst \
    | tar --zstd -x -C /usr

WORKDIR /opt/guardian-src
COPY ai-gateway/pyproject.toml ai-gateway/README.md ./
COPY ai-gateway/gateway ./gateway
RUN python3.12 -m venv /opt/guardian-venv \
    && /opt/guardian-venv/bin/pip install --no-cache-dir --upgrade pip \
    && /opt/guardian-venv/bin/pip install --no-cache-dir .

# Dependencies used by the supplied ABAP Expert agent.py.
RUN python3.12 -m venv /opt/agent-venv \
    && /opt/agent-venv/bin/pip install --no-cache-dir --upgrade pip \
    && /opt/agent-venv/bin/pip install --no-cache-dir \
        chromadb \
        fastapi \
        jinja2 \
        ollama \
        pydantic \
        pymupdf \
        python-docx \
        python-multipart \
        uvicorn

RUN mkdir -p /opt/guardian/knowledge/docs /opt/guardian/knowledge/rules /opt/runpod
COPY docs /opt/guardian/knowledge/docs
COPY rules /opt/guardian/knowledge/rules
COPY --from=analyzer-build /build/analyzer-core/target/analyzer-core-*.jar /opt/guardian/analyzer.jar

COPY deploy/runpod/run.sh /opt/runpod/run.sh
COPY deploy/runpod/start-runpod-base.sh /opt/runpod/start-runpod-base.sh
COPY deploy/runpod/start-ollama.sh /opt/runpod/start-ollama.sh
COPY deploy/runpod/start-agent.sh /opt/runpod/start-agent.sh
COPY deploy/runpod/start-guardian.sh /opt/runpod/start-guardian.sh
COPY deploy/runpod/supervisord.conf /opt/runpod/supervisord.conf

# The base image defines a port-8001 proxy directly in nginx.conf and does not
# load conf.d. Guardian owns 8001, so move that unused proxy to loopback-only
# port 18001 and load the authenticated Agent browser proxy on 8002.
RUN rm -f /etc/nginx/sites-enabled/* /etc/nginx/conf.d/*.conf
COPY deploy/runpod/nginx-agent-web.conf /etc/nginx/conf.d/abap-agent-web.conf
RUN sed -i 's/listen 8001;/listen 127.0.0.1:18001;/' /etc/nginx/nginx.conf \
    && sed -i '/^http {/a\    include /etc/nginx/conf.d/*.conf;' /etc/nginx/nginx.conf \
    && grep -Fq 'listen 127.0.0.1:18001;' /etc/nginx/nginx.conf \
    && grep -Fq 'include /etc/nginx/conf.d/*.conf;' /etc/nginx/nginx.conf \
    && ! grep -Eq 'listen[[:space:]]+8001;' /etc/nginx/nginx.conf \
    && install -d -o root -g www-data -m 0750 /run/abap-guardian \
    && install -o root -g www-data -m 0640 /dev/null /run/abap-guardian/agent-web.htpasswd \
    && nginx -t \
    && rm -rf /run/abap-guardian

RUN sed -i 's/\r$//' /opt/runpod/*.sh \
    && chmod 0755 /opt/runpod/*.sh

EXPOSE 8001 8002
CMD ["/opt/runpod/run.sh"]
```

The Ollama model weights are deliberately not part of the image. Ollama's
manual Linux installation package is documented at
[Ollama for Linux](https://docs.ollama.com/linux).

### 8.2 `deploy/runpod/run.sh`

```bash
#!/usr/bin/env bash
set -Eeuo pipefail

mkdir -p \
  /workspace/abap-stack/data/ollama \
  /workspace/abap-stack/logs \
  /workspace/abap-stack/secrets

exec /usr/bin/supervisord -n -c /opt/runpod/supervisord.conf
```

### 8.3 `deploy/runpod/start-runpod-base.sh`

This wrapper creates a bcrypt password file without exposing the password in
the process list. It then starts RunPod's normal base services with application
secrets removed from the child environment so `/start.sh` cannot export them
to `/etc/rp_environment`.

```bash
#!/usr/bin/env bash
set -Eeuo pipefail

: "${AGENT_WEB_PASSWORD:?Set AGENT_WEB_PASSWORD with a RunPod secret}"

agent_web_username="${AGENT_WEB_USERNAME:-guardian}"
nginx_auth_group="${NGINX_AUTH_GROUP:-www-data}"

if [[ ! "$agent_web_username" =~ ^[A-Za-z0-9._-]{1,64}$ ]]; then
  echo "AGENT_WEB_USERNAME contains unsupported characters" >&2
  exit 1
fi

if (( ${#AGENT_WEB_PASSWORD} < 24 )); then
  echo "AGENT_WEB_PASSWORD must contain at least 24 characters" >&2
  exit 1
fi

case "$AGENT_WEB_PASSWORD" in
  *$'\n'*|*$'\r'*)
    echo "AGENT_WEB_PASSWORD must not contain line breaks" >&2
    exit 1
    ;;
esac

if ! getent group "$nginx_auth_group" >/dev/null; then
  echo "Nginx authentication group does not exist: $nginx_auth_group" >&2
  exit 1
fi

umask 027
install -d -o root -g "$nginx_auth_group" -m 0750 /run/abap-guardian
printf '%s\n' "$AGENT_WEB_PASSWORD" \
  | /usr/bin/htpasswd -ciB \
      /run/abap-guardian/agent-web.htpasswd \
      "$agent_web_username" \
      >/dev/null
chown root:"$nginx_auth_group" /run/abap-guardian/agent-web.htpasswd
chmod 0640 /run/abap-guardian/agent-web.htpasswd

/usr/sbin/nginx -t

exec /usr/bin/env \
  -u AGENT_WEB_PASSWORD \
  -u GUARDIAN_API_TOKEN \
  -u ADMIN_API_KEY \
  /start.sh
```

### 8.4 `deploy/runpod/nginx-agent-web.conf`

The proxy applies Basic Authentication to every browser route and removes the
credential before forwarding traffic to Agent on loopback.

```nginx
map $http_upgrade $abap_guardian_connection_upgrade {
    default upgrade;
    ''      close;
}

server {
    listen 8002 default_server;
    server_name _;
    server_tokens off;

    auth_basic "ABAP Guardian Agent";
    auth_basic_user_file /run/abap-guardian/agent-web.htpasswd;

    client_max_body_size 25m;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_connect_timeout 10s;
        proxy_send_timeout 95s;
        proxy_read_timeout 95s;
        proxy_buffering off;
        proxy_request_buffering off;

        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $abap_guardian_connection_upgrade;
        proxy_set_header Authorization "";
    }
}
```

### 8.5 `deploy/runpod/start-ollama.sh`

```bash
#!/usr/bin/env bash
set -Eeuo pipefail

export OLLAMA_HOST="127.0.0.1:11434"
export OLLAMA_MODELS="${OLLAMA_MODELS:-/workspace/abap-stack/data/ollama}"
mkdir -p "$OLLAMA_MODELS"

exec /usr/bin/ollama serve
```

### 8.6 `deploy/runpod/start-agent.sh`

```bash
#!/usr/bin/env bash
set -Eeuo pipefail

AGENT_DIR="${ABAP_AGENT_DIR:-/workspace/abap-stack/abap-agent}"
CHAT_MODEL="${CHAT_MODEL:-abap-expert}"
EMBEDDING_MODEL="${EMBEDDING_MODEL:-embeddinggemma}"

until [[ -f "$AGENT_DIR/agent.py" && -f "$AGENT_DIR/Modelfile" ]]; do
  echo "Waiting for agent.py and Modelfile in $AGENT_DIR"
  sleep 10
done

export OLLAMA_HOST="http://127.0.0.1:11434"
export OLLAMA_MODELS="${OLLAMA_MODELS:-/workspace/abap-stack/data/ollama}"
export CHAT_MODEL EMBEDDING_MODEL
export KNOWLEDGE_DIR="${KNOWLEDGE_DIR:-$AGENT_DIR/knowledge}"
export UPLOAD_DIR="${UPLOAD_DIR:-$KNOWLEDGE_DIR/uploads}"
export DATABASE_DIR="${DATABASE_DIR:-$AGENT_DIR/vector_db}"
export TEMPLATES_DIR="${TEMPLATES_DIR:-$AGENT_DIR/templates}"
export STATIC_DIR="${STATIC_DIR:-$AGENT_DIR/static}"
export CORS_ORIGINS=""

mkdir -p "$KNOWLEDGE_DIR" "$UPLOAD_DIR" "$DATABASE_DIR" "$TEMPLATES_DIR" "$STATIC_DIR"

until /usr/bin/ollama list >/dev/null 2>&1; do
  echo "Waiting for Ollama"
  sleep 3
done

if ! /usr/bin/ollama show "$EMBEDDING_MODEL" >/dev/null 2>&1; then
  /usr/bin/ollama pull "$EMBEDDING_MODEL"
fi

if ! /usr/bin/ollama show "$CHAT_MODEL" >/dev/null 2>&1; then
  /usr/bin/ollama create "$CHAT_MODEL" -f "$AGENT_DIR/Modelfile"
fi

exec /opt/agent-venv/bin/python "$AGENT_DIR/agent.py" web \
  --host 127.0.0.1 \
  --port 8000 \
  --no-browser
```

### 8.7 `deploy/runpod/start-guardian.sh`

```bash
#!/usr/bin/env bash
set -Eeuo pipefail

: "${GUARDIAN_API_TOKEN:?Set GUARDIAN_API_TOKEN with a RunPod secret}"

until curl -fsS http://127.0.0.1:8000/api/status >/dev/null; do
  echo "Waiting for ABAP Expert RAG service"
  sleep 5
done

export LLM_PROVIDER="abap-agent"
export ABAP_AGENT_BASE_URL="http://127.0.0.1:8000"
export ABAP_AGENT_MODEL="${CHAT_MODEL:-abap-expert}"
export ALLOW_EXTERNAL_PROVIDERS="false"
export REDACTION_ENABLED="true"
export BUNDLED_KNOWLEDGE_PATH=""
export REQUIRE_API_AUTH="true"
export RATE_LIMIT_PER_MINUTE="${RATE_LIMIT_PER_MINUTE:-60}"
export MAX_SOURCE_LENGTH="${MAX_SOURCE_LENGTH:-200000}"
export MAX_REQUEST_BODY_BYTES="${MAX_REQUEST_BODY_BYTES:-1048576}"
export REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-90}"
export AI_TIMEOUT_SECONDS="${AI_TIMEOUT_SECONDS:-90}"
export MAX_TOKENS="${MAX_TOKENS:-1024}"
export ANALYZER_JAR="/opt/guardian/analyzer.jar"
export JAVA_BIN="/usr/bin/java"

cd /opt/guardian-src
exec /opt/guardian-venv/bin/uvicorn gateway.main:app \
  --host 0.0.0.0 \
  --port 8001 \
  --no-access-log
```

### 8.8 `deploy/runpod/supervisord.conf`

```ini
[supervisord]
nodaemon=true
logfile=/workspace/abap-stack/logs/supervisord.log
pidfile=/tmp/supervisord.pid
childlogdir=/workspace/abap-stack/logs

[unix_http_server]
file=/tmp/supervisor.sock
chmod=0700

[rpcinterface:supervisor]
supervisor.rpcinterface_factory=supervisor.rpcinterface:make_main_rpcinterface

[supervisorctl]
serverurl=unix:///tmp/supervisor.sock

[program:runpod-base]
command=/opt/runpod/start-runpod-base.sh
priority=10
autostart=true
autorestart=true
startsecs=5
stdout_logfile=/workspace/abap-stack/logs/runpod-base.log
stderr_logfile=/workspace/abap-stack/logs/runpod-base-error.log

[program:ollama]
command=/opt/runpod/start-ollama.sh
priority=20
autostart=true
autorestart=true
startsecs=5
stopasgroup=true
killasgroup=true
stdout_logfile=/workspace/abap-stack/logs/ollama.log
stderr_logfile=/workspace/abap-stack/logs/ollama-error.log

[program:abap-agent]
command=/opt/runpod/start-agent.sh
priority=30
autostart=true
autorestart=true
startsecs=10
startretries=1000
stopasgroup=true
killasgroup=true
stdout_logfile=/workspace/abap-stack/logs/abap-agent.log
stderr_logfile=/workspace/abap-stack/logs/abap-agent-error.log

[program:guardian]
command=/opt/runpod/start-guardian.sh
priority=40
autostart=true
autorestart=true
startsecs=10
startretries=1000
stopasgroup=true
killasgroup=true
stdout_logfile=/workspace/abap-stack/logs/guardian.log
stderr_logfile=/workspace/abap-stack/logs/guardian-error.log
```

### 8.9 `deploy/runpod/Dockerfile.dockerignore`

The filename is intentional: because the build context is the repository root,
Docker uses this Dockerfile-specific ignore file next to `Dockerfile`.

```gitignore
.git
.github
**/.venv
**/__pycache__
**/*.pyc
deploy/gpu-vm/secrets
*.zip
*.tar.gz
```

## 9. Build the RunPod image on Windows

Open PowerShell in the Guardian repository root:

```powershell
Set-Location <GUARDIAN_REPO>

docker build `
  --pull `
  --no-cache `
  --platform linux/amd64 `
  --file deploy/runpod/Dockerfile `
  --tag <DOCKER_USER>/abap-guardian-runpod:0.4.0-runpod5 `
  .
```

This must finish successfully. Then push the immutable version tag:

```powershell
docker push <DOCKER_USER>/abap-guardian-runpod:0.4.0-runpod5
```

Do not rely on a floating `latest` tag for the production Pod. A versioned tag
makes rollback and troubleshooting predictable.

## 10. Add private-registry credentials to RunPod

In RunPod, create a container registry credential using:

- Name: `dockerhub-abap-guardian`
- Username: your Docker Hub username
- Password: the Docker Hub access token, not the normal account password

RunPod stores private-registry credentials as write-only values. See
[RunPod private registry credentials](https://docs.runpod.io/api-reference-v2/registries/create-a-container-registry-credential).

## 11. Create the application secrets

Generate three different cryptographically random values in PowerShell:

```powershell
function New-RandomHexToken {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    return -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

$guardianToken = New-RandomHexToken
$agentAdminToken = New-RandomHexToken
$agentWebPassword = New-RandomHexToken

$guardianToken | Set-Clipboard
Write-Host "Guardian token copied; save it in the password manager and RunPod secret."
```

Do not print or screenshot the values. Save each one in the password manager
and its corresponding RunPod secret before copying the next value:

```powershell
$agentAdminToken | Set-Clipboard
Write-Host "Agent administrator token copied."

$agentWebPassword | Set-Clipboard
Write-Host "Agent website password copied."
```

In **RunPod → Secrets**, create:

| Secret name | Value |
| --- | --- |
| `guardian_api_token` | value of `$guardianToken` |
| `abap_agent_admin_key` | value of `$agentAdminToken` |
| `abap_agent_web_password` | value of `$agentWebPassword` |

After saving, remove the plaintext variables from the PowerShell session:

```powershell
$guardianToken = $null
$agentAdminToken = $null
$agentWebPassword = $null
Clear-Clipboard
```

RunPod secret values cannot be viewed after creation. Templates reference them
with `{{ RUNPOD_SECRET_secret_name }}`. See
[RunPod Secrets](https://docs.runpod.io/pods/templates/secrets).

## 12. Create persistent storage

Use a **Network Volume** so the data survives even if the Pod is terminated and
recreated.

1. Open **Storage → Network Volumes**.
2. Choose a Secure Cloud data centre in an approved European location.
3. Create a volume of at least `150 GB`.
4. Name it `abap-guardian-data`.
5. Record the selected data centre because the attached Pod must use compatible
   capacity there.

Store all persistent content under `/workspace`. RunPod clears the container
disk when a Pod stops, while network volumes persist independently from the
Pod. Network volumes below 1 TB are currently billed separately per GB-month;
verify the current value in the console. See
[RunPod storage types](https://docs.runpod.io/pods/storage/types) and
[storage pricing](https://docs.runpod.io/pods/pricing).

## 13. Create a private RunPod template

Open **Templates → New Template** and configure:

| Setting | Value |
| --- | --- |
| Name | `ABAP Guardian Ollama RAG 0.4.0` |
| Container image | `<DOCKER_USER>/abap-guardian-runpod:0.4.0-runpod5` |
| Registry credentials | `dockerhub-abap-guardian` |
| Container disk | `20 GB` |
| Volume mount path | `/workspace` |
| Expose HTTP ports | `8001,8002` |
| Expose TCP ports | `22` |

Add these environment variables:

| Name | Value |
| --- | --- |
| `CHAT_MODEL` | `abap-expert` |
| `EMBEDDING_MODEL` | `embeddinggemma` |
| `GUARDIAN_API_TOKEN` | `{{ RUNPOD_SECRET_guardian_api_token }}` |
| `ADMIN_API_KEY` | `{{ RUNPOD_SECRET_abap_agent_admin_key }}` |
| `AGENT_WEB_USERNAME` | `guardian` |
| `AGENT_WEB_PASSWORD` | `{{ RUNPOD_SECRET_abap_agent_web_password }}` |
| `RATE_LIMIT_PER_MINUTE` | `60` |
| `MAX_SOURCE_LENGTH` | `200000` |
| `MAX_REQUEST_BODY_BYTES` | `1048576` |
| `REQUEST_TIMEOUT_SECONDS` | `90` |
| `AI_TIMEOUT_SECONDS` | `90` |
| `MAX_TOKENS` | `1024` |

Do not add an OpenAI key. Do not expose ports `8000`, `11434` or the Jupyter
port `8888`. Port `8002` is safe to expose only through the included Nginx
Basic Authentication proxy.

## 14. Deploy the GPU Pod

1. Open **Pods → Deploy**.
2. Choose **Secure Cloud**.
3. Select the `abap-guardian-data` network volume first.
4. Select a compatible European data centre.
5. Select one 24 GB GPU, preferably L4, A5000, RTX 3090 or RTX 4090.
6. Select the private template `ABAP Guardian Ollama RAG 0.4.0`.
7. Enable SSH terminal access/public IP support.
8. Confirm the hourly price.
9. Choose **Deploy On-Demand**.

Do not use Spot/interruptible capacity for the first installation. The Pod can
be stopped later when not needed.

After the Pod reaches **Running**, open **Connect** and record:

- Pod ID;
- public IP address;
- external TCP port mapped to internal port 22;
- Guardian HTTP service for port 8001;
- authenticated Agent website for port 8002.

The service is not healthy yet because `agent.py` and `Modelfile` have not been
transferred. The agent process intentionally waits for them.

## 15. Transfer the private ABAP Agent and knowledge

On Windows, open PowerShell in the folder containing `abap-agent`:

```powershell
Set-Location <AGENT_PARENT>

tar `
  --exclude="abap-agent/.venv" `
  --exclude="abap-agent/vector_db" `
  --exclude="abap-agent/__pycache__" `
  -czf abap-agent-runpod.tar.gz `
  abap-agent
```

The existing vector database is excluded so it can be rebuilt using the cloud
embedding model. Transfer the archive using the exact IP and port shown by
RunPod:

```powershell
scp `
  -P <SSH_PORT> `
  -i "$HOME\.ssh\abap-guardian-runpod" `
  .\abap-agent-runpod.tar.gz `
  root@<POD_IP>:/workspace/
```

Connect with SSH:

```powershell
ssh `
  -p <SSH_PORT> `
  -i "$HOME\.ssh\abap-guardian-runpod" `
  root@<POD_IP>
```

On the Pod:

```bash
mkdir -p /workspace/abap-stack
tar -xzf /workspace/abap-agent-runpod.tar.gz -C /workspace/abap-stack

mkdir -p /workspace/abap-stack/abap-agent/knowledge/uploads
mkdir -p /workspace/abap-stack/abap-agent/vector_db

find /workspace/abap-stack/abap-agent -maxdepth 2 -type f -print
```

Confirm at minimum:

```text
/workspace/abap-stack/abap-agent/agent.py
/workspace/abap-stack/abap-agent/Modelfile
/workspace/abap-stack/abap-agent/knowledge/...
/workspace/abap-stack/abap-agent/templates/index.html
/workspace/abap-stack/abap-agent/static/...
```

Only after confirming extraction, remove the transfer archive:

```bash
rm /workspace/abap-agent-runpod.tar.gz
```

Supervisor retries automatically. Once it sees `agent.py` and `Modelfile`, it
downloads `embeddinggemma`, creates `abap-expert`, starts the RAG service and
then starts Guardian. The first start can take several minutes.

## 16. Monitor the first startup

On the Pod:

```bash
supervisorctl -c /opt/runpod/supervisord.conf status
```

Expected final state:

```text
runpod-base   RUNNING
ollama        RUNNING
abap-agent    RUNNING
guardian      RUNNING
```

Watch model loading:

```bash
tail -f /workspace/abap-stack/logs/abap-agent.log
```

Press `Ctrl+C` to stop following the log; the services continue running.

Other useful logs:

```bash
tail -n 200 /workspace/abap-stack/logs/ollama.log
tail -n 200 /workspace/abap-stack/logs/abap-agent-error.log
tail -n 200 /workspace/abap-stack/logs/guardian-error.log
```

Verify the GPU and models:

```bash
nvidia-smi
OLLAMA_HOST=http://127.0.0.1:11434 ollama list
```

The list must contain `abap-expert` and `embeddinggemma`.

## 17. Build the cloud knowledge index

The agent's administrator endpoint is internal. Run on the Pod:

```bash
curl --fail --silent --show-error \
  -X POST \
  -H "X-Admin-Key: ${ADMIN_API_KEY}" \
  http://127.0.0.1:8000/api/index \
  | /opt/agent-venv/bin/python -m json.tool
```

The result should report the number of knowledge files and indexed chunks.
Indexing can take time but is not subject to the public proxy's 100-second
limit because this request stays inside the Pod.

Check status:

```bash
curl --fail --silent http://127.0.0.1:8000/api/status \
  | /opt/agent-venv/bin/python -m json.tool
```

Confirm:

```text
chat_model_available: true
embedding_model_available: true
indexed_chunks: greater than 0
admin_endpoints_protected: true
```

Warm the chat model before the first Eclipse request:

```bash
curl --fail --silent --show-error --no-buffer \
  -H "Content-Type: application/json" \
  -d '{"message":"Give one short ABAP SELECT example.","history":[]}' \
  http://127.0.0.1:8000/api/chat \
  >/dev/null
```

## 18. Verify Guardian inside the Pod

```bash
curl --fail --silent http://127.0.0.1:8001/health \
  | /opt/agent-venv/bin/python -m json.tool
```

Expected properties:

```json
{
  "status": "ok",
  "llmAvailable": true,
  "llmProvider": "abap-agent",
  "analyzerAvailable": true,
  "authenticationRequired": true,
  "authenticationConfigured": true,
  "version": "0.4.0"
}
```

An unauthenticated protected request must return HTTP `401`:

```bash
curl -i http://127.0.0.1:8001/api/v1/models
```

An authenticated request must succeed:

```bash
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${GUARDIAN_API_TOKEN}" \
  http://127.0.0.1:8001/api/v1/models \
  | /opt/agent-venv/bin/python -m json.tool
```

Confirm that Guardian, not Nginx, owns port 8001 and that Nginx owns only the
authenticated browser port:

```bash
ss -lntp | grep -E ':8001|:8002'
```

Expected process mapping:

```text
8001 -> uvicorn
8002 -> nginx
```

The browser port must reject a request without credentials:

```bash
curl --silent --output /dev/null \
  --write-out "HTTP status: %{http_code}\n" \
  http://127.0.0.1:8002/
```

Expected result: `HTTP status: 401`.

## 19. Verify the public HTTPS endpoint from Windows

Build the URL using the Pod ID:

```powershell
$podId = "<POD_ID>"
$guardianUrl = "https://${podId}-8001.proxy.runpod.net"

Invoke-RestMethod "$guardianUrl/health" |
  ConvertTo-Json -Depth 5
```

Verify that authentication is enforced:

```powershell
try {
    Invoke-WebRequest "$guardianUrl/api/v1/models"
} catch {
    $_.Exception.Response.StatusCode.value__
}
```

Expected result:

```text
401
```

Test the token without displaying it or adding it to a script file:

```powershell
$guardianTokenSecure = Read-Host "Guardian API token" -AsSecureString
$guardianToken = [System.Net.NetworkCredential]::new(
  "",
  $guardianTokenSecure
).Password
$headers = @{ Authorization = "Bearer $guardianToken" }

Invoke-RestMethod `
  -Uri "$guardianUrl/api/v1/models" `
  -Headers $headers |
  ConvertTo-Json -Depth 5

$guardianToken = $null
$guardianTokenSecure = $null
$headers = $null
```

Open the protected Agent website in a browser:

```text
https://<POD_ID>-8002.proxy.runpod.net
```

Use username `guardian` and the password stored in the
`abap_agent_web_password` RunPod secret. The browser can now reach Agent while
the Pod is running; no SSH tunnel or continuously running local PowerShell
session is required. Never expose Agent's internal port `8000` or Ollama port
`11434`.

## 20. Install or update ABAP Guardian in Eclipse

### Fresh installation

1. Open Eclipse/ADT.
2. Choose **Help → Install New Software…**.
3. In **Work with**, enter:

   ```text
   https://kostasppz.github.io/abap-zcopilot/
   ```

4. Select **ABAP Guardian**.
5. Continue through the installation and restart Eclipse.

### Existing installation

1. Choose **Help → Check for Updates**.
2. Install ABAP Guardian `0.4.0` or newer.
3. Restart Eclipse.
4. Open **Window → Preferences → ABAP Guardian**.
5. Confirm that an **API token** field is present.

If no token field exists, stop here. The installed plug-in is too old for a
secure public RunPod deployment.

## 21. Configure Eclipse for RunPod

Open **Window → Preferences → ABAP Guardian** and set:

| Setting | Value |
| --- | --- |
| Service URL | `https://<POD_ID>-8001.proxy.runpod.net` |
| Request timeout | `95` seconds |
| API token | value of RunPod secret `guardian_api_token` |
| Store securely | Enabled |
| Use online AI enhancement | Enabled |
| Live AI analysis | Disabled initially |

Do not append `/api`, `/api/v1`, `:8000` or `:11434` to the URL.

Select **Test Connection**. The expected result is that Guardian is reachable
and the token is accepted. Eclipse stores the token in Eclipse Secure Storage,
not ordinary workspace preferences.

## 22. Use ABAP Guardian in Eclipse

1. Start the RunPod Pod and wait until `/health` reports `status: ok`.
2. Open an ABAP object in ADT.
3. Press `Ctrl+Alt+C` to open **ABAP Guardian Copilot**.
4. Ask a question about the active object.
5. Select ABAP code and use **Explain selection** or **Suggest correction**.
6. Press `Ctrl+Alt+G` to analyze the current object.
7. Review findings in **Guardian Findings**.
8. Review the diff before accepting any correction.

Start with manual analysis. Automatic AI analysis sends more requests and may
consume more GPU time.

Recommended initial preferences:

| Option | Initial value |
| --- | --- |
| Manual deterministic analysis | Enabled |
| Copilot chat | Enabled |
| Analyze on save | Optional after testing |
| Live analysis after typing | Disabled |
| Automatic AI enhancement | Disabled |
| Request timeout | 95 seconds |

## 23. Understand the 100-second proxy limit

RunPod's proxy closes an HTTP request after 100 seconds. A larger Eclipse
timeout cannot override this limit.

If Eclipse receives HTTP `524` or times out:

1. Warm the Ollama model using Step 17.
2. Confirm `nvidia-smi` shows the model using the GPU.
3. Keep `AI_TIMEOUT_SECONDS` at `90` or less.
4. Keep `MAX_TOKENS` at `1024` initially.
5. Ask for smaller answers or reduce selected source size.
6. Use a faster GPU if ordinary requests still exceed the limit.
7. In `agent.py`, add `"num_predict": 1024` to the Ollama `options` object if
   responses are excessively long, then restart the agent.

The timeout is one reason this tutorial uses an always-running Pod instead of
serverless cold starts for the first deployment.

## 24. Stop and restart the Pod safely

When the AI is not needed:

1. Open the RunPod Pod page.
2. Choose **Stop**.
3. Confirm it is stopped.

Stopping releases the GPU. The network volume and its storage charges remain.
When the same Pod is started again:

- the custom container image is recreated;
- `/workspace` is reattached;
- Ollama models, knowledge and Chroma data remain;
- Supervisor restarts the services;
- Nginx exposes the password-protected Agent website on port 8002;
- the Guardian proxy URL remains based on the same Pod ID;
- the SSH external port may change, so check **Connect** again.

Do not choose **Terminate** unless the network volume is attached and you have
confirmed the data location. A network volume persists independently, but a
normal volume disk is deleted with its Pod. See
[RunPod storage choices](https://docs.runpod.io/pods/choose-a-pod).

## 25. Update Guardian and the RunPod image

After a Guardian code change, build a new immutable tag on Windows:

```powershell
Set-Location <GUARDIAN_REPO>
git pull --ff-only

docker build `
  --platform linux/amd64 `
  --file deploy/runpod/Dockerfile `
  --tag <DOCKER_USER>/abap-guardian-runpod:0.4.1-runpod1 `
  .

docker push <DOCKER_USER>/abap-guardian-runpod:0.4.1-runpod1
```

Then:

1. Edit the private RunPod template.
2. Replace the old image tag with the new tag.
3. Update/reset the Pod using the new image.
4. Verify the attached network volume before confirming.
5. Re-run the health and authentication tests.

The container disk is recreated, but `/workspace` remains on the network
volume.

## 26. Update agent source or knowledge

Transfer changed private files with SCP using the current RunPod SSH mapping.
After agent source changes:

```bash
supervisorctl -c /opt/runpod/supervisord.conf restart abap-agent
supervisorctl -c /opt/runpod/supervisord.conf restart guardian
```

After knowledge changes, rebuild the index:

```bash
curl --fail --silent --show-error \
  -X POST \
  -H "X-Admin-Key: ${ADMIN_API_KEY}" \
  http://127.0.0.1:8000/api/index \
  | /opt/agent-venv/bin/python -m json.tool
```

If `Modelfile` changes, recreate the custom model explicitly:

```bash
OLLAMA_HOST=http://127.0.0.1:11434 \
  ollama create abap-expert \
  -f /workspace/abap-stack/abap-agent/Modelfile

supervisorctl -c /opt/runpod/supervisord.conf restart abap-agent
supervisorctl -c /opt/runpod/supervisord.conf restart guardian
```

## 27. Back up the knowledge and vector database

Create an archive on the Pod:

```bash
mkdir -p /workspace/abap-stack/backups

tar -czf "/workspace/abap-stack/backups/abap-agent-$(date +%F).tar.gz" \
  -C /workspace/abap-stack/abap-agent \
  knowledge vector_db Modelfile
```

An archive on the same volume is not a complete backup. Download it to an
approved encrypted destination using SCP:

```powershell
scp `
  -P <SSH_PORT> `
  -i "$HOME\.ssh\abap-guardian-runpod" `
  root@<POD_IP>:/workspace/abap-stack/backups/abap-agent-YYYY-MM-DD.tar.gz `
  .
```

RunPod network volumes also have an S3-compatible access route. Use it only
with an approved backup destination and securely managed S3 credentials. See
[RunPod S3-compatible storage](https://docs.runpod.io/storage/s3-api).

Ollama models can be recreated from `Modelfile`, so private knowledge and the
`Modelfile` are the most important backup. Backing up `vector_db` avoids a full
re-index but does not replace the source knowledge files.

## 28. Rotate the Guardian API token

1. Generate a new token with `New-RandomHexToken` from Step 11.
2. Update the RunPod secret `guardian_api_token`.
3. Restart/update the Pod so the new secret is injected.
4. Replace the token in every authorized Eclipse installation.
5. Test the connection.

The old token stops working after Guardian restarts with the new value.

Rotate `abap_agent_admin_key` separately. Never reuse the Eclipse token as the
agent administrator key. Rotate `abap_agent_web_password` separately and
update saved browser credentials when necessary.

## 29. Troubleshooting

Run this first on the Pod:

```bash
supervisorctl -c /opt/runpod/supervisord.conf status
nvidia-smi
OLLAMA_HOST=http://127.0.0.1:11434 ollama list
curl -s http://127.0.0.1:8000/api/status | /opt/agent-venv/bin/python -m json.tool
curl -s http://127.0.0.1:8001/health | /opt/agent-venv/bin/python -m json.tool
```

| Symptom | Likely cause and action |
| --- | --- |
| `abap-agent` remains STARTING/BACKOFF | Check that `agent.py` and `Modelfile` exist at the exact paths; inspect `abap-agent-error.log`. |
| `guardian` does not start | Agent health is unavailable or `GUARDIAN_API_TOKEN` was not injected. |
| Nginx or `runpod-base` does not start | Verify `AGENT_WEB_PASSWORD` is injected from the RunPod secret and contains at least 24 characters. |
| Agent website returns 401 | Use username `guardian` and the current `abap_agent_web_password`; remove stale credentials saved by the browser. |
| Agent website returns Nginx 500 after login | Verify `/run/abap-guardian` is group-readable by `www-data`; use image tag `0.4.0-runpod5` or newer. |
| `llmAvailable` is false | Verify both model names, Ollama logs and `/api/status`. |
| Out of GPU memory | Use a smaller quantization/model or a 48 GB GPU. |
| Public health returns 502 | Guardian is not listening on `0.0.0.0:8001`; check Supervisor and logs. |
| Eclipse receives 401 | Token in Eclipse does not match the RunPod secret. |
| Eclipse receives 429 | The per-token/client rate limit was reached; wait or adjust carefully. |
| Eclipse receives 524 | Request exceeded RunPod proxy's 100-second limit; warm/reduce/upgrade as described in Step 23. |
| Knowledge is ignored | Rebuild the index and confirm `indexed_chunks` is greater than zero. |
| SCP fails after restart | The external SSH port changed; copy the new command from **Connect**. |
| Data disappeared after stop | It was written outside `/workspace`, or a nonpersistent container path was used. |
| Private image cannot be pulled | Verify the image tag and RunPod registry credential. |

Never troubleshoot by exposing ports `8000` or `11434` publicly.

## 30. Complete cost cleanup

When the deployment is no longer needed:

1. Download the final approved backup.
2. Terminate the Pod.
3. Delete the network volume only after confirming the backup.
4. Remove unused RunPod registry credentials and secrets.
5. Delete obsolete private Docker image tags if desired.
6. Disable auto-pay if it is no longer needed.
7. Confirm the RunPod billing page shows no running compute or unwanted
   storage.

Deleting the network volume is permanent. Do not delete it merely to stop GPU
charges; stopping the Pod already releases GPU compute.

## 31. Production checklist

- Secure Cloud and the selected European data location are organizationally
  approved.
- The custom image is private and referenced by an immutable version tag.
- Only `8001/http`, authenticated `8002/http` and administration `22/tcp` are exposed.
- Ports `8000`, `11434` and `8888` are not exposed.
- Guardian requires a random bearer token.
- The Agent website requires a separate random Basic Authentication password.
- The token is stored in RunPod Secrets, a password manager and Eclipse Secure
  Storage.
- The agent administrator key is different from the Guardian token.
- The network volume is attached at `/workspace`.
- Knowledge, Chroma and Ollama models are under `/workspace`.
- Low-balance alerts are enabled.
- The public unauthenticated API test returns `401`.
- Backups have been downloaded and restore-tested.
- AI output remains subject to activation, ATC and human review.

## Official references

- [RunPod Pods overview](https://docs.runpod.io/pods/overview)
- [RunPod custom templates](https://docs.runpod.io/pods/templates/create-custom-template)
- [RunPod Secrets](https://docs.runpod.io/pods/templates/secrets)
- [RunPod HTTP/TCP port exposure](https://docs.runpod.io/pods/configuration/expose-ports)
- [RunPod SSH](https://docs.runpod.io/pods/configuration/use-ssh)
- [RunPod storage types](https://docs.runpod.io/pods/storage/types)
- [RunPod network volumes](https://docs.runpod.io/storage/network-volumes)
- [RunPod Pod pricing](https://docs.runpod.io/pods/pricing)
- [RunPod billing](https://docs.runpod.io/accounts-billing/billing)
- [Ollama Linux installation](https://docs.ollama.com/linux)
