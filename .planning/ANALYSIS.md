# Codebase + Requirements Analysis (Relación)

> Snapshot taken 2026-05-03. Refresh whenever the starter changes.

## 1. What ships in the starter

```
document-management-service-challenge/
├── README.md                       # Challenge spec
├── pom.xml                         # Spring Boot 3.4.3, Java 17, MinIO 8.4.3, JaCoCo, Spotless
├── mvnw / mvnw.cmd / .mvn/         # Maven wrapper (3.9.9)
├── .gitignore / .gitattributes
├── docker/
│   ├── docker-compose.yml          # PostgreSQL + MinIO; service stub commented out
│   └── init-scripts/
│       └── schema-init.sql         # Stub: only creates `document_schema` schema
├── docs/
│   ├── document-management-open-api.yml   # API contract (3 endpoints)
│   ├── minio-local-setup.md               # Operator guide for MinIO
│   └── assets/minio-access-key.png
└── src/
    ├── main/java/com/clara/ops/challenge/document_management_service_challenge/
    │   └── DocumentManagementServiceChallengeApplication.java   # @SpringBootApplication only
    └── test/java/.../DocumentManagementServiceChallengeApplicationTests.java   # contextLoads()
```

Missing artifacts to be built: `application.yml`, `Dockerfile`, domain/web/storage code, real schema DDL, `.env.example`, tests beyond the trivial context-load test.

## 2. Dependencies in pom.xml (current)

| Group | Artifact | Notes |
|---|---|---|
| `org.springframework.boot` | `spring-boot-starter-data-jpa` | Hibernate, JPA, JDBC |
| `org.springframework.boot` | `spring-boot-starter-web` | Spring MVC, Tomcat, Jackson |
| `org.postgresql` | `postgresql` | JDBC driver, runtime |
| `io.minio` | `minio:8.4.3` | MinIO Java SDK (8.5.x available — bump in Phase 1) |
| `org.springframework.boot` | `spring-boot-devtools` | Optional, runtime |
| `org.projectlombok` | `lombok` | Boilerplate reduction |
| `org.springframework.boot` | `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ, Spring Test |

Build plugins: `spring-boot-maven-plugin`, `maven-surefire-plugin`, `maven-compiler-plugin` (Lombok APT path), `jacoco-maven-plugin:0.8.8` (declared **twice** — dedupe), `spotless-maven-plugin:2.43.0` bound to `check`.

**Missing (to add):** `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `springdoc-openapi-starter-webmvc-ui`, `commons-fileupload2-jakarta`, Testcontainers BOM + `postgresql` + `minio` + `junit-jupiter`, `logstash-logback-encoder`.

## 3. Requirements decomposition (README + OpenAPI)

### 3.1 Functional

**POST `/document-management/upload`** → 201
- OpenAPI: JSON `{ user, name, tags[] }`. **No file binary in spec.**
- README requires storing the PDF in MinIO at `document-bucket/{user}/{name}.pdf`.
- Resolution: deviate to `multipart/form-data` (ADR-0011) — JSON-only is incompatible with the 50 MB heap × 500 MB file constraint.
- DB metadata fields: user, name, tags, MinIO path, file size, file type, created_at, plus `id`.

**POST `/document-management/search`** → 200 `PaginatedDocumentSearch`
- Filters: optional `user`, `name`, `tags[]`. No filters → all.
- Order: `created_at DESC`.
- `page`, `size`, `sort` query params (Spring `Pageable`-shaped).
- **Must not** return any download URL.

**GET `/document-management/download/{documentId}`** → 200 `DocumentDownloadUrl`
- Returns `{ url: "<minio-presigned>" }`. Service does not stream the PDF.

### 3.2 Non-functional

- 50 MB heap, 500 MB max file, 10 concurrent uploads.
- All config via env vars.
- Schema script in `docker/init-scripts/schema-init.sql` (autoloaded by Postgres on first volume init).
- Dockerfile + service entry in `docker/docker-compose.yml` with memory limit.
- Tests for critical paths and edge cases.
- Optional: OpenAPI documentation served at runtime (springdoc).

## 4. Risks and gotchas surfaced by the starter

1. **OpenAPI contradicts a streaming upload.** Resolved with multipart deviation (ADR-0011).
2. **`schema-init.sql` references "Spring Batch Core"** in its comment — leftover noise; we own and rewrite this file.
3. **`docker-compose.yml` uses `{CHANGE_ME}` literal placeholders.** Must be replaced with env-var interpolation (`${POSTGRESQL_USERNAME}` etc.) and a committed `.env.example`.
4. **MinIO 8.4.3** — older client. Bump to `8.5.7+` recommended (bug fixes around presigned URL host header handling).
5. **JaCoCo plugin declared twice** in pom (lines 86–90 and 103–121). First block is dead; clean up during config phase.
6. **No `application.yml`** — every property is a fresh decision; capture defaults explicitly.
7. **Spotless `<execution>` with `check` goal** at default phase fails CI if not formatted. Document in README.
8. **PostgreSQL bitnami uses `/bitnami/postgresql`** as data dir — different from Docker-Hub `postgres`. Affects volume reasoning.
9. **No `created_at`-friendly index in the empty schema** — we own the DDL.
10. **Devtools is in runtime classpath**. Should be excluded from the prod image.
11. **schema-init scripts run only on first volume init** — `docker compose down -v` is required to re-apply DDL during dev iteration.

## 5. What we are explicitly NOT building

- Authentication / authorization (out of scope per challenge).
- Multi-region / replication.
- Document text indexing / full-text search (only metadata search per spec).
- Antivirus scanning (interface stubbed only if time permits).
- A frontend.

## 6. Decision queue

| # | Decision | ADR |
|---|---|---|
| 1 | Architectural style | 0001 |
| 2 | Streaming upload mechanics | 0002 |
| 3 | DB schema for documents + tags | 0003 |
| 4 | MinIO usage + presigned URL TTL | 0004 |
| 5 | Concurrency model | 0005 |
| 6 | Error handling shape | 0006 |
| 7 | Testing strategy | 0007 |
| 8 | Configuration management | 0008 |
| 9 | Validation + DTOs | 0009 |
| 10 | Observability | 0010 |
| 11 | API contract deviation (multipart upload) | 0011 |
