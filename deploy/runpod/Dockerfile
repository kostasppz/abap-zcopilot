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

# The RunPod base image ships a default Nginx site on port 8001. Guardian owns
# that port, so replace every default site with the authenticated Agent proxy
# installed below. The proxy listens on 8002 and keeps Agent itself on loopback.
RUN rm -f /etc/nginx/sites-enabled/* /etc/nginx/conf.d/*.conf
COPY deploy/runpod/nginx-agent-web.conf /etc/nginx/conf.d/abap-agent-web.conf
RUN install -d -m 0700 /run/abap-guardian \
    && : > /run/abap-guardian/agent-web.htpasswd \
    && nginx -t \
    && rm -rf /run/abap-guardian

# Windows Git clients can still provide CRLF files through an existing
# checkout. Normalize them in the image as a second line of defense.
RUN sed -i 's/\r$//' /opt/runpod/*.sh \
    && chmod 0755 /opt/runpod/*.sh

EXPOSE 8001 8002
CMD ["/opt/runpod/run.sh"]
