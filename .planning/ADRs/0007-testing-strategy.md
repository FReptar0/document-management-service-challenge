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

### Heap-bounded concurrency test
- The 50 MB heap is a contract. We assert it.
- Test runs `@SpringBootTest` under `-Xmx50m -Xms50m` (forked Surefire JVM via `argLine`).
- 10 parallel uploads of 100 MB synthetic streams (random bytes, `InputStream` wrapping a deterministic generator — no on-disk fixture needed).
- Sample `ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()` every 100 ms during the run; assert peak ≤ 45 MB (gives a small safety margin under the 50 MB cap to account for GC headroom).
- All 10 uploads must succeed; objects must be retrievable; DB rows must exist.
- Marked `@Tag("stress")` and excluded from the default Surefire run; included in `verify` and a dedicated `./mvnw test -Dgroups=stress` invocation.

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
