# Quality report

Snapshot of the latest `./mvnw verify` run. Regenerate by running:

```bash
./mvnw verify
# Coverage HTML:  target/site/jacoco/index.html
# Surefire XML:   target/surefire-reports/
```

- **Last regenerated:** 2026-05-05
- **Build status:** `BUILD SUCCESS`
- **Maven goal:** `verify` (Spotless format check + tests + JaCoCo coverage report)
- **Java:** 17.0.19 (Temurin) · **Maven Wrapper:** 3.9.9 · **Spring Boot:** 3.4.3
- **Containers used by integration tests:** `postgres:15-alpine`, `minio/minio:latest` (via Testcontainers — Docker must be running locally)

## Code style — Spotless

Spotless is bound to the `check` phase in `pom.xml` and would fail the build on any deviation. The latest run is clean:

```
[INFO] --- spotless:2.43.0:check (default) @ document-management-service-challenge ---
[INFO] Spotless.Java is keeping 58 files clean
[INFO] Spotless.Markdown is keeping 21 files clean
```

Run `./mvnw spotless:apply` to autofix; `./mvnw spotless:check` to gate.

## Unit + integration tests — Surefire

`Tests run: 40, Failures: 0, Errors: 0, Skipped: 0`

|                                       Test class                                       |  Tests |      Time |
|----------------------------------------------------------------------------------------|-------:|----------:|
| `com.clara.ops.challenge.dms.application.ObjectKeyStrategyTest`                        |      7 |    0.10 s |
| `com.clara.ops.challenge.dms.application.UploadDocumentUseCaseTest`                    |      3 |    0.91 s |
| `com.clara.ops.challenge.dms.application.SearchDocumentsUseCaseTest`                   |      2 |    3.81 s |
| `com.clara.ops.challenge.dms.application.GetDownloadUrlUseCaseTest`                    |      2 |    0.13 s |
| `com.clara.ops.challenge.dms.infrastructure.persistence.DocumentRepositoryAdapterTest` |      3 |    1.14 s |
| `com.clara.ops.challenge.dms.infrastructure.storage.MinioDocumentStoragePortTest`      |      2 |    3.78 s |
| `com.clara.ops.challenge.dms.infrastructure.web.multipart.PdfMagicByteSnifferTest`     |      4 |    0.02 s |
| `com.clara.ops.challenge.dms.infrastructure.web.UploadEndpointIntegrationTest`         |      7 |    6.84 s |
| `com.clara.ops.challenge.dms.infrastructure.web.SearchEndpointIntegrationTest`         |      5 |    7.78 s |
| `com.clara.ops.challenge.dms.infrastructure.web.DownloadEndpointIntegrationTest`       |      2 |    6.28 s |
| `com.clara.ops.challenge.dms.infrastructure.web.EndToEndDocumentLifecycleTest`         |      1 |    6.51 s |
| `com.clara.ops.challenge.dms.infrastructure.web.TagNormalizationE2ETest`               |      1 |   36.76 s |
| `com.clara.ops.challenge.dms.infrastructure.web.ConcurrentUploadStressTest`            |      1 |   12.81 s |
| **Total**                                                                              | **40** | **~87 s** |

What each layer proves:

- **`application/*Test`** — pure unit tests with Mockito-mocked ports; cover use-case branches (compensation on DB failure, criteria pass-through, 404 vs presigned URL, object-key sanitization).
- **`infrastructure/persistence/*Test`** — `@DataJpaTest` against a real Postgres container; race-safe `INSERT … ON CONFLICT (name) DO NOTHING` tag upsert exercised.
- **`infrastructure/storage/*Test`** — `@SpringBootTest` against a real MinIO container; `put`, `presignedGet`, `delete` round-tripped.
- **`infrastructure/web/*EndpointIntegrationTest`** — `@SpringBootTest(RANDOM_PORT)` over real HTTP including the streaming multipart parser; happy paths and 400/413/415/404 negatives.
- **`EndToEndDocumentLifecycleTest`** — upload → search → download → fetch presigned URL, byte-identical round-trip across the whole stack.
- **`TagNormalizationE2ETest`** — three uploads with `Finance` / `FINANCE` / `"  finance  "` collapse to one tag row; all three are findable via the canonical name.
- **`ConcurrentUploadStressTest`** — 10 parallel streaming uploads of 10 MB each (both client and server bodies are streaming); asserts every request returns 201 and every row lands. The 50 MB heap cap is enforced at the deployment layer (compose `JAVA_OPTS=-Xmx50m`, ADR-0012).

## Coverage — JaCoCo

Generated agent-instrumented during `verify`; full HTML drilldown lives at `target/site/jacoco/index.html`.

```
Instructions: 1505/1797   (83.8%)
Branches:       61/  94   (64.9%)
Lines:         362/ 425   (85.2%)
Methods:       110/ 125   (88.0%)
Complexity:    127/ 172   (73.8%)
```

Per-package line coverage:

|                             Package                             | Lines covered |    % |
|-----------------------------------------------------------------|--------------:|-----:|
| `com.clara.ops.challenge.dms.application`                       |         67/71 |  94% |
| `com.clara.ops.challenge.dms.domain`                            |         39/51 |  76% |
| `com.clara.ops.challenge.dms.domain.exception`                  |           4/4 | 100% |
| `com.clara.ops.challenge.dms.infrastructure.config`             |           5/5 | 100% |
| `com.clara.ops.challenge.dms.infrastructure.persistence`        |         44/46 |  96% |
| `com.clara.ops.challenge.dms.infrastructure.persistence.entity` |         11/14 |  79% |
| `com.clara.ops.challenge.dms.infrastructure.persistence.mapper` |         20/20 | 100% |
| `com.clara.ops.challenge.dms.infrastructure.storage`            |         46/58 |  79% |
| `com.clara.ops.challenge.dms.infrastructure.web`                |         54/67 |  81% |
| `com.clara.ops.challenge.dms.infrastructure.web.advice`         |         16/25 |  64% |
| `com.clara.ops.challenge.dms.infrastructure.web.dto`            |         22/22 | 100% |
| `com.clara.ops.challenge.dms.infrastructure.web.multipart`      |         23/29 |  79% |
| `com.clara.ops.challenge.dms.infrastructure.web.observability`  |         10/10 | 100% |
| `com.clara.ops.challenge.dms` (boot class)                      |           1/3 |  33% |

Notes:

- The 64% on `web/advice` is dominated by defensive branches in `GlobalExceptionAdvice` for failure modes that are not reachable from any of the integration tests (e.g., the catch-all `Exception` handler — by design we never throw an unmapped exception in normal flow). Covered branches include the production-path mappings: 404, 400, 413, 415.
- The 33% on the root package is the `main(String[])` of the Spring Boot application class; it is exercised end-to-end every time `@SpringBootTest` boots a context, but JaCoCo flags it as "main not invoked directly" — a well-known noise pattern that we intentionally do not chase.
- Coverage is a *signal*, not a gate (see ADR-0007). The application layer (`application/*` 94%) and domain (`domain/exception/*` 100%, `domain/*` 76%) are where regressions matter most; integration tests carry the real verification weight.

