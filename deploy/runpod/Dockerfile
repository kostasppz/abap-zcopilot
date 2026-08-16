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
COPY deploy/runpod/start-ollama.sh /opt/runpod/start-ollama.sh
COPY deploy/runpod/start-agent.sh /opt/runpod/start-agent.sh
COPY deploy/runpod/start-guardian.sh /opt/runpod/start-guardian.sh
COPY deploy/runpod/supervisord.conf /opt/runpod/supervisord.conf
RUN chmod 0755 /opt/runpod/*.sh

EXPOSE 8001
CMD ["/opt/runpod/run.sh"]
