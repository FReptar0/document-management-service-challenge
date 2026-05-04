# ADR-0005: Synchronous request lifecycle, sized Tomcat thread pool, no async

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** concurrency, threading, tomcat

## Context

README requires 10 concurrent 500 MB uploads. Each upload is an open HTTP request streaming bytes from client → service → MinIO. There is no work to offload to a background queue; the upload IS the request. The 50 MB heap budget makes thread proliferation expensive — every extra thread carries stack, ThreadLocal storage, and the risk of holding a part buffer.

## Decision

- **Synchronous Spring MVC request handling.** Each upload occupies one Tomcat worker for the request's duration.
- Tomcat tuning (in `application.yml`):

```yaml
server:
  tomcat:
    threads:
      max: 20            # 10 upload + headroom for search/download
      min-spare: 5
    max-connections: 200
    accept-count: 50
    connection-timeout: 20000
    keep-alive-timeout: 15000
    max-http-form-post-size: -1   # we parse multipart ourselves; do not let Tomcat enforce
spring:
  mvc:
    async:
      request-timeout: -1     # no MVC async; safety
  servlet:
    multipart:
      enabled: false          # disabled per ADR-0002
```

- **No `@Async`, no custom thread pools.** The thread that accepts the request does the parse-and-stream work directly.
- **Backpressure via thread cap.** Excess concurrent requests queue (`accept-count: 50`); beyond that, the client receives a connect refusal — natural backpressure without any application-level rate limiter.

## Consequences

- **Positive:** Simple model. Failures are local to one request. No background-state surprises. Heap accounting per ADR-0002 holds: heap usage = (active requests) × (per-request budget).
- **Negative:** A slow client occupies a thread for minutes; under DoS this is a vector. **Out of scope** for the challenge (no auth, no rate limiting). Flagged in `GOTCHAS.md`.
- **Risks:** Tomcat default keep-alive can hold idle threads. **Mitigation:** explicit `keep-alive-timeout: 15000`.

## Alternatives considered

- **`@Async` upload + 202 Accepted + status endpoint.** Better UX for very large files but doubles the API surface and changes the OpenAPI contract.
- **Spring WebFlux.** See `ADR-0002` for rejection rationale.

## Links

- README §"Concurrent Uploads"
- `ADR-0002` (heap budget)

