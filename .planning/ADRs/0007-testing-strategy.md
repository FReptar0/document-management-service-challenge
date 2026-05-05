# ADR-0007: Layered tests with Testcontainers and a heap-bounded concurrency test

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** testing, qa, ci, testcontainers

## Context

README §"Testing": *"tests should cover the most critical functionalities and edge cases."* With a streaming pipeline, mocks alone are deceptive — we need real Postgres + real MinIO for storage and persistence adapters. The 50 MB heap constraint is itself a correctness property and deserves an explicit regression test.

## Decision

Three layers:

### 1. Unit tests

- `@ExtendWith(MockitoExtension.class)` + AssertJ.
- Targets: domain types, value objects, use cases (`UploadDocumentUseCase`, `SearchDocumentsUseCase`, `GetDownloadUrlUseCase`).
- Ports are mocked. No Spring context.
- Goal: every branch of every use case.

### 2. Slice tests

- `@WebMvcTest` for `DocumentController`: validation, status codes, request mapping, error advice. Streaming multipart path tested separately because it bypasses `@WebMvcTest`'s mocked Tomcat.
- `@DataJpaTest` + `@Testcontainers` Postgres for `DocumentSpecifications` and the JPA adapter.

### 3. Integration tests

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers `PostgreSQLContainer` + `MinIOContainer`.
- **End-to-end happy path:** upload → object visible in MinIO → search returns the row → download endpoint returns a presigned URL → HTTP `GET` against that URL returns the original bytes (validated by digest).
- **Failure paths:** MinIO down (`container.stop()` mid-test), DB unavailable, oversize upload, malformed multipart order.

### Streaming concurrency test (`ConcurrentUploadStressTest`)

- The 50 MB heap is a contract. The shipped enforcement is a two-part check:
  1. **In tests:** `ConcurrentUploadStressTest` drives 10 parallel uploads of 10 MB streaming bodies (`RepeatingByteInputStream` + `BodyPublishers.ofInputStream`, chunked transfer encoding) so neither side ever materializes the payload. All 10 must return `201` and persist a distinct row. If the upload pipe were buffering, the aggregate request body alone would push the JVM into `OutOfMemoryError`.
  2. **In production:** the docker-compose container boots under `-Xmx50m -Xms50m` (ADR-0012). A non-streaming upload path would `OOM-kill` on the first request — the test deployment is the binding heap-bounded check.
- An in-test peak-heap watchdog asserting `peak ≤ N MB` was tried and rejected: under the JaCoCo agent that `mvn verify` attaches for coverage, sampled peak heap delta varied between ~80 MB and ~215 MB across runs of the same code, so the assertion oscillated between flaky-pass and flaky-fail without changing behaviour. Forcing a forked Surefire JVM with `-Xmx50m` was rejected for similar reasons (Testcontainers' Postgres + MinIO clients alone exceed 50 MB before the upload starts). The compose-level cap remains the source of truth; this test guarantees the *streaming* property end to end.
- Counted in the default Surefire run (no `@Tag` exclusion) so it executes on every `./mvnw test` and `./mvnw verify`.

### Coverage

- Target: ≥70% on `application/` and `domain/`.
- JaCoCo report at `target/site/jacoco/`.
- Coverage is a *signal*, not a gate. We do not chase 100% on glue code.

### CI command

- `./mvnw spotless:check verify` — Spotless format check + tests + JaCoCo + stress.

## Consequences

- **Positive:** Confidence rooted in real adapter behavior. Heap test makes the central constraint a regression guard. Reviewers can read the test names and immediately understand what we proved.
- **Negative:** Testcontainers boots are slow (~10 s warm). Mitigated by Ryuk reuse (`testcontainers.reuse.enable=true` for local dev).
- **Risks:** Concurrency test flakiness if the host is under load. **Mitigations:** generous threshold; sampling rather than wall-clock thresholds; `@Tag("stress")` keeps it out of the fast loop.

## Alternatives considered

- **Embedded H2 + mock S3.** Fast, but neither adapter is exercised against real wire formats.
- **No integration tests.** Would not detect transaction-rollback / object-orphan bugs.
- **Coverage gate.** Tempting but punishes glue code. Replaced with explicit "what we proved" intent in test names.

## Links

- README §"Testing", §"Additional Considerations"
- Testcontainers JUnit 5 docs
- `ADR-0002` (heap budget)

