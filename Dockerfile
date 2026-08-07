FROM maven:3.9-eclipse-temurin-21 AS analyzer-build

WORKDIR /build
COPY pom.xml ./
COPY analyzer-core/pom.xml analyzer-core/pom.xml
COPY analyzer-core/src analyzer-core/src
RUN mvn -B -pl analyzer-core -am clean package -DskipTests

FROM python:3.12-slim

# Reuse the Java 21 runtime from the build image. This avoids depending on a
# distribution-specific apt package while keeping one deployable container.
COPY --from=analyzer-build /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /app
COPY ai-gateway/pyproject.toml ai-gateway/README.md ./
COPY ai-gateway/gateway ./gateway
RUN python -m pip install --no-cache-dir .

COPY --from=analyzer-build /build/analyzer-core/target/analyzer-core-*.jar /app/analyzer.jar

ENV ANALYZER_JAR=/app/analyzer.jar
ENV PORT=8000
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:' + __import__('os').environ.get('PORT', '8000') + '/health', timeout=3)"

CMD ["sh", "-c", "uvicorn gateway.main:app --host 0.0.0.0 --port ${PORT}"]
