# ADR-0010: Structured logging with MDC, Actuator health, Springdoc OpenAPI UI

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** observability, logging, ops

## Context
A 50 MB JVM under 10 concurrent multi-minute streams is going to surprise us. Without structured logs and request correlation, debugging the inevitable streaming edge case is a slog. The README also lists OpenAPI documentation as optional but valued ("Additional Considerations").

## Decision

### Logging
- SLF4J + Logback (Spring Boot defaults).
- `logback-spring.xml` selects the JSON encoder (`net.logstash.logback:logstash-logback-encoder`) when `LOG_FORMAT=json` (the default in the container) and a plain pattern otherwise (default in tests / dev shell).
- Standard fields: `timestamp`, `level`, `logger`, `message`, plus MDC: `requestId`, `userId`, `documentId`, `objectKey`, `bytesStreamed` (when relevant).
- Levels:
  - `INFO` — request start/end, upload completion (with `bytesStreamed`, duration), MinIO bucket bootstrap.
  - `WARN` — validation failures, missing-part errors, presigned URL miss (404).
  - `ERROR` — storage / DB failures with exception cause.
- **Never** log file bodies. **Never** log presigned URLs at `INFO` (treat as ephemeral secrets).

### Request correlation
- `RequestIdFilter` (`OncePerRequestFilter`) populates MDC `requestId` from `X-Request-Id` header or a fresh `UUID`. Echoes the chosen value back in the response header. Cleared in `finally`.

### Actuator
- `spring-boot-starter-actuator` enabled.
- Exposed: `health`, `info`. Everything else disabled by default (`management.endpoints.web.exposure.include=health,info`).
- Custom indicators: `MinioHealthIndicator` (lists buckets, fast call), `DataSourceHealthIndicator` (built-in via Spring Boot).
- `info` includes build version + git commit (via `git-commit-id-maven-plugin` if added; otherwise omitted).

### OpenAPI runtime UI
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`.
- UI at `/swagger-ui.html`, JSON at `/v3/api-docs`.
- Description seeded from the static `docs/document-management-open-api.yml` so reviewers see the contract immediately. The streaming multipart endpoint is described accurately (not the original JSON-only shape — see `ADR-0011`).

## Consequences
- **Positive:** Easy to correlate logs across the upload pipeline; evaluators get a self-documenting endpoint; Actuator gives a fast smoke test in CI.
- **Negative:** Logback JSON encoder pulls a transitive dependency (~200 KB). Acceptable.

## Alternatives considered
- **OpenTelemetry / distributed tracing.** Out of scope for a single-service challenge.
- **Prometheus metrics now.** Defer; Micrometer is a small addition if time permits (see TODOs floating section).

## Links
- README §"Additional Considerations" (OpenAPI optional)
- `ADR-0006` (errors carry `requestId`)
- `ADR-0011` (OpenAPI accurately reflects multipart deviation)
