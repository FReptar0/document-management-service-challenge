# Roadmap

Phased plan from empty starter to submission-ready. Each phase ends with a green build, a passing smoke test, and a Conventional Commit. Phases 0–10 below; granular tasks live in `TODOS.md`.

## Phase 0 — Map & decide *(this phase)*

**Output:** `CLAUDE.md`, `.planning/ANALYSIS.md`, `.planning/ROADMAP.md`, `.planning/TODOS.md`, `.planning/GOTCHAS.md`, ADRs 0001–0011.
**Exit:** All planning artifacts committed in one `chore(docs): bootstrap planning artifacts` commit.

## Phase 1 — Foundation (config + container)

- `application.yml` with env-var overrides for DB and MinIO
- Dependency additions per `TODOS.md` Phase 1
- `docker/docker-compose.yml` updated: env interpolation, `document-management-service` uncommented with 50 MB limit, healthchecks
- Multi-stage `Dockerfile` (Eclipse Temurin JRE 17, devtools/lombok excluded, non-root)
- `.env.example`
- **Exit smoke test:** `docker compose up --build` boots clean; `GET /actuator/health` returns 200.

## Phase 2 — Schema

- Real `schema-init.sql`: `documents`, `tags`, `document_tags`, indices, constraints
- `pg_trgm` extension for name search
- Verify schema applied at container boot
- **Exit:** `docker exec ... psql -c '\dt document_schema.*'` lists 3 tables.

## Phase 3 — Domain + Persistence

- Domain types: `Document`, `Tag`, value objects
- JPA entities + Spring Data repositories
- Mappers domain ↔ entity
- `@DataJpaTest` repository slice with Testcontainers Postgres
- **Exit:** Repository slice tests green.

## Phase 4 — Storage adapter (MinIO)

- `DocumentStoragePort` (upload stream, presigned GET URL)
- `MinioDocumentStorageAdapter`
- Bucket bootstrap on startup
- Integration test via Testcontainers MinIO
- **Exit:** Object written + presigned URL retrievable end-to-end.

## Phase 5 — Upload endpoint *(the hard one)*

- `POST /document-management/upload` (multipart: `metadata` + `file`)
- Streaming parse via `commons-fileupload2-jakarta` on raw `HttpServletRequest`
- Pipe `file` part directly to `DocumentStoragePort.put(InputStream)` with 5 MB part size
- Persist metadata only after storage write succeeds (compensating delete on DB failure)
- **Exit:** 200 MB synthetic upload completes, heap stays < 40 MB, DB row created, object visible in MinIO.

## Phase 6 — Search endpoint

- `POST /document-management/search`
- JPA Specifications for dynamic filters
- Default sort `created_at DESC`, pagination
- DTO mapping (no `url` in response per spec)
- **Exit:** Filter combinations + pagination edges green.

## Phase 7 — Download endpoint

- `GET /document-management/download/{documentId}` returns `{ url }`
- Configurable presigned TTL (default 15 min)
- 404 on missing
- **Exit:** Presigned URL works against MinIO container.

## Phase 8 — Cross-cutting

- `@RestControllerAdvice` global handler with RFC 7807 ProblemDetail
- Bean validation
- Structured JSON logging with MDC `requestId`
- Spring Boot Actuator (health/info) + custom indicators for DB and MinIO
- Springdoc OpenAPI UI at `/swagger-ui.html`
- **Exit:** Errors return RFC 7807 envelopes; `/swagger-ui.html` renders the contract.

## Phase 9 — Test hardening

- E2E happy path (upload → search → download → fetch via presigned URL)
- Concurrency stress test (10 parallel × 100 MB streams)
- MinIO-down and DB-down failure paths
- JaCoCo report; review uncovered branches
- **Exit:** ≥70% coverage on `application/` and `domain/`.

## Phase 10 — Polish + submission

- Rewrite root `README.md` (run/test/verify, env matrix, design notes, deviations, omissions)
- Postman collection in `docs/postman/`
- Final `./mvnw spotless:apply && ./mvnw verify`
- Verify `docker stats` confirms 50 MB cap respected during upload
- Push to personal GitHub repo
- **Exit:** Submission URL ready.

