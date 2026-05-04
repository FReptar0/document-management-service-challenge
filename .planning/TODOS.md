# Actionable TODOs

> Source of truth for in-flight work. Mark `[x]` on completion, `[/]` for partial. Add new items as they surface; never silently drop one. Phase tags map to `ROADMAP.md`.

## Phase 0 — Map & decide

- [x] Map starter codebase (files, deps, gaps)
- [x] Decompose README + OpenAPI into requirements
- [x] Write `CLAUDE.md`
- [x] Write `.planning/ANALYSIS.md` (relación)
- [x] Write `.planning/ROADMAP.md`
- [x] Write `.planning/TODOS.md` (this file)
- [x] Seed `.planning/GOTCHAS.md`
- [x] ADR-0001 architecture style
- [x] ADR-0002 streaming upload strategy
- [x] ADR-0003 database schema design
- [x] ADR-0004 storage and presigned URLs
- [x] ADR-0005 concurrency model
- [x] ADR-0006 error handling strategy
- [x] ADR-0007 testing strategy
- [x] ADR-0008 configuration management
- [x] ADR-0009 validation and DTO strategy
- [x] ADR-0010 observability strategy
- [x] ADR-0011 API contract deviation (multipart upload)
- [ ] First commit: `chore(docs): bootstrap planning artifacts (CLAUDE.md, ADRs, roadmap)`

## Phase 1 — Foundation

- [x] `src/main/resources/application.yml` (datasource, JPA, MinIO, multipart, server, logging)
- [x] `src/main/resources/application-test.yml` (Testcontainers profile via Surefire argLine)
- [x] Add `org.springframework.boot:spring-boot-starter-validation`
- [x] Add `org.springframework.boot:spring-boot-starter-actuator`
- [x] Add `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- [x] Add `org.apache.commons:commons-fileupload2-jakarta-servlet6`
- [x] Add `net.logstash.logback:logstash-logback-encoder`
- [x] Add Testcontainers BOM + `postgresql` + `minio` modules + `junit-jupiter`
- [x] Bump `io.minio:minio` to 8.5.7
- [x] Dedupe duplicated JaCoCo plugin block in pom.xml
- [x] Exclude `spring-boot-devtools` from `spring-boot-maven-plugin` build image
- [x] Replace `{CHANGE_ME}` literals in `docker/docker-compose.yml` with `${VAR}` interpolation
- [x] Uncomment + complete `document-management-service` block (memory cap per ADR-0012, env, depends_on healthchecks)
- [x] Write multi-stage `Dockerfile` (cached deps layer, Temurin JRE 17, non-root user)
- [x] `.env.example` with every required var
- [x] ADR-0012: container memory headroom above heap (deviation documented)
- [x] `docker compose up --build` boots clean; `/actuator/health` returns `{"status":"UP"}` (verified 2026-05-04)
- [x] Commits — split into atomic units (`chore(deps)`, `feat(config)`, `chore(config)`, `feat(infra)`, `docs(adr)`, `fix(infra)`)

## Phase 2 — Schema

- [x] Design + write `docker/init-scripts/schema-init.sql`
  - [x] `documents` (id UUID PK, user_id, name, object_key UNIQUE, file_size, file_type, created_at)
  - [x] `tags` (id BIGSERIAL, name UNIQUE, max 64 chars)
  - [x] `document_tags` (document_id, tag_id) — composite PK
  - [x] Indices: `documents(user_id, created_at DESC)`, `documents(name) gin_trgm`, `document_tags(tag_id, document_id)`
  - [x] `CREATE EXTENSION IF NOT EXISTS pg_trgm`
- [x] Verify schema applied: 3 tables + 8 indices + pg_trgm 1.6 confirmed via psql
- [x] Commit: `feat(db): documents/tags schema with indices and pg_trgm`

## Phase 3 — Domain + Persistence

- [x] Package layout under `com.clara.ops.challenge.dms`: `domain/`, `application/`, `infrastructure/{web,persistence,storage,config}`
- [x] `domain/Document.java`, `domain/Tag.java`
- [x] `domain/port/DocumentRepository.java` (port)
- [x] `domain/exception/DocumentNotFoundException.java`
- [x] `infrastructure/persistence/{entity,jpa,mapper}` — entities + Spring Data repos + static mapper
- [x] `infrastructure/persistence/DocumentRepositoryAdapter.java` with race-safe tag upsert
- [x] `@DataJpaTest` slice + Testcontainers Postgres + schema script via testResources
- [x] Commits: `feat(domain): rename to dms; Document/Tag/port`, `feat(persistence): JPA adapter`, `test(persistence): slice with Testcontainers`

## Phase 4 — Storage adapter

- [x] `domain/port/DocumentStoragePort.java` (`put(InputStream, key, contentType)`, `presignedGet(key, ttl)`, `delete(key)`)
- [x] `infrastructure/storage/MinioDocumentStoragePort.java`
- [x] `infrastructure/storage/MinioProperties.java` (`@ConfigurationProperties("app.minio")`)
- [x] Dual `MinioClient` beans (internal `@Primary` + `@Qualifier("minioPublic")`) so presigned URLs sign against the public host
- [x] Bucket bootstrap on startup (`MinioBucketBootstrap` ApplicationRunner)
- [x] Integration test with Testcontainers MinIO; assert presigned URL returns the bytes we wrote
- [x] Commits: `feat(storage): MinIO adapter…`, `test(storage): Testcontainers integration…`

## Phase 5 — Upload endpoint

### 5.1 Application layer (use case + helpers)

- [x] `application/ObjectKeyStrategy.java` (`{userId}/{documentId}__{sanitized-name}.pdf`)
- [x] `application/CountingInputStream.java` (size discovery without buffering)
- [x] `application/UploadDocumentUseCase.java` (storage-first, then DB; compensating delete on persistence failure)
- [x] `infrastructure/config/UseCaseConfiguration.java` (Spring wiring; use case stays framework-free per ADR-0001)
- [x] Unit tests: `ObjectKeyStrategyTest` (7), `UploadDocumentUseCaseTest` (3) — all green
- [x] Commit: `feat(application): UploadDocumentUseCase with key strategy and byte counter`

### 5.2 Web layer (multipart endpoint)

- [ ] `infrastructure/web/DocumentController.upload`
- [ ] Multipart contract: `metadata` (application/json) + `file` (application/pdf), in that order
- [ ] Disable Spring's default multipart resolver (`spring.servlet.multipart.enabled=false`)
- [ ] `infrastructure/web/multipart/MultipartStreamReader` wrapping `commons-fileupload2-jakarta`
- [ ] `infrastructure/web/UploadProperties` (`@ConfigurationProperties("app.upload")` — max size, allowed type)
- [ ] DTOs: `UploadMetadataRequest`, `UploadResponse`
- [ ] Optional PDF magic-byte sniff at stream start (configurable)
- [ ] Custom multipart exceptions → mapped to 400/413/415 in Phase 8 advice
- [ ] Commit: `feat(web): streaming multipart upload endpoint with size + type guards`

### 5.3 Integration tests

- [ ] Full-stack upload integration test (Postgres + MinIO TC + real HTTP): 201 happy path
- [ ] Oversize → 413, non-PDF → 415 (enforcement on), missing metadata → 400, file-before-metadata → 400
- [ ] Concurrency test: 10 × 100 MB streams under `-Xmx50m`; assert success + heap < 40 MB
- [ ] Commit: `test(upload): full-stack streaming upload integration test`

## Phase 6 — Search endpoint

- [ ] `infrastructure/web/DocumentController.search`
- [ ] `application/SearchDocumentsUseCase`
- [ ] `infrastructure/persistence/DocumentSpecifications` for `user`, `name LIKE`, `tags ANY`
- [ ] Default sort `created_at DESC`
- [ ] Response DTO: no `url` field
- [ ] Tests: empty filters, single filter, all filters, no results, pagination first/last/empty page
- [ ] Commit: `feat(search): paginated document search with composable filters`

## Phase 7 — Download endpoint

- [ ] `infrastructure/web/DocumentController.download`
- [ ] `application/GetDownloadUrlUseCase`
- [ ] Presigned URL with TTL from config
- [ ] 404 on missing document
- [ ] Tests: happy path, missing id, expired URL behavior documented
- [ ] Commit: `feat(download): presigned download URL by document id`

## Phase 8 — Cross-cutting

- [ ] `@RestControllerAdvice` global handler with RFC 7807 ProblemDetail
- [ ] Bean validation on DTOs (`@Valid`)
- [ ] `RequestIdFilter` to set MDC `requestId` (`X-Request-Id` header echo)
- [ ] Structured JSON logging via `logback-spring.xml`
- [ ] Actuator: health (db + minio), info (build version)
- [ ] Springdoc UI at `/swagger-ui.html`
- [ ] Commit: `feat(observability): RFC7807 errors, structured logs, actuator, OpenAPI UI`

## Phase 9 — Test hardening

- [ ] E2E happy path (upload → search → download → fetch via presigned)
- [ ] Concurrency stress test (10 parallel × 100 MB)
- [ ] MinIO-down failure path (Testcontainers stop / network alias swap)
- [ ] DB-down failure path
- [ ] Generate JaCoCo report; review uncovered branches
- [ ] Commit: `test: e2e, concurrency, and resilience suites`

## Phase 10 — Polish + submission

- [ ] Rewrite root `README.md` (run/test/verify, env matrix, design notes, deviations, omissions)
- [ ] Postman collection in `docs/postman/`
- [ ] `./mvnw spotless:apply && ./mvnw verify` — all green
- [ ] Verify `docker stats` confirms 50 MB cap respected during upload
- [ ] Push to personal GitHub
- [ ] Final commit: `docs: submission-ready README and Postman collection`

## Floating / nice-to-have

- [ ] Rate limiter on upload (per user) — only if time permits
- [ ] Virus scan hook (no-op adapter, real implementation deferred)
- [ ] Micrometer metrics: upload bytes/sec, failed uploads counter
- [ ] GitHub Actions CI: spotless + verify on push

