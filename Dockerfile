# syntax=docker/dockerfile:1.7
# Multi-stage build for the Document Management Service.
# - Stage 1 (build) downloads deps once and builds the Spring Boot fat JAR.
# - Stage 2 (runtime) ships only the JRE + JAR; non-root user; container HEALTHCHECK.

# =========================
# Stage 1 — build
# =========================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies in a separate layer so source-only changes do not invalidate the cache.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests dependency:go-offline

# Compile + package. Tests and Spotless are validated outside the image (faster CI loop).
COPY src/ ./src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -DskipTests -Dspotless.check.skip=true package \
    && cp target/document-management-service-challenge-*-LOCAL.jar /workspace/app.jar

# =========================
# Stage 2 — runtime
# =========================
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# curl is used by the HEALTHCHECK below. Non-root user reduces RCE blast radius.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r dms \
    && useradd -r -g dms -u 10001 -d /app -s /usr/sbin/nologin dms \
    && chown -R dms:dms /app
USER 10001

# Heap fixed at 50 MB per README §"Memory Limitation" (ADR-0002 enforces the streaming budget;
# ADR-0012 explains why the *container* limit is set higher to allow JVM startup overhead).
ENV JAVA_OPTS="-Xmx50m -Xms50m -XX:+ExitOnOutOfMemoryError -XX:MaxMetaspaceSize=64m"
ENV SERVER_PORT=8080
EXPOSE 8080

COPY --from=build --chown=dms:dms /workspace/app.jar /app/app.jar

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=8 \
    CMD curl -fsS "http://localhost:${SERVER_PORT}/actuator/health" || exit 1

# `exec` ensures the JVM becomes PID 1 and receives SIGTERM directly from Docker.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
