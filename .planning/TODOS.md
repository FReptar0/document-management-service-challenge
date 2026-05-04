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
- [ ] `docker compose up --build` boots clean; `/actuator/health` returns 200 — **smoke test pending**
- [x] Commits — split into atomic units (`chore(deps)`, `feat(config)`, `chore(config)`, `feat(infra)`, `docs(adr)`)

## Phase 2 — Schema
- [ ] Design + write `docker/init-scripts/schema-init.sql`
  - [ ] `documents` (id UUID PK, user_id, name, object_key UNIQUE, file_size, file_type, created_at)
  - [ ] `tags` (id BIGSERIAL, name UNIQUE, max 64 chars)
  - [ ] `document_tags` (document_id, tag_id) — composite PK
  - [ ] Indices: `documents(user_id, created_at DESC)`, `documents(name) gin_trgm`, `document_tags(tag_id, document_id)`
  - [ ] `CREATE EXTENSION IF NOT EXISTS pg_trgm`
- [ ] Verify schema applied: `docker exec ... psql -c '\dt document_schema.*'`
- [ ] Commit: `feat(db): documents/tags schema with required indices`

## Phase 3 — Domain + Persistence
- [ ] Package layout under `com.clara.ops.challenge.dms`: `domain/`, `application/`, `infrastructure/{web,persistence,storage,config}`
- [ ] `domain/Document.java`, `domain/Tag.java`
- [ ] `domain/port/DocumentRepository.java` (port)
- [ ] `domain/exception/DocumentNotFoundException.java`
- [ ] `infrastructure/persistence/DocumentEntity.java`, `TagEntity.java`, JPA repositories
- [ ] `infrastructure/persistence/DocumentJpaRepositoryAdapter.java`
- [ ] Mapper(s) domain ↔ entity
- [ ] `@DataJpaTest` slice + Testcontainers Postgres
- [ ] Commit: `feat(persistence): document repository and JPA adapter`

## Phase 4 — Storage adapter
- [ ] `domain/port/DocumentStoragePort.java` (`put(InputStream, key, contentType)`, `presignedGet(key, ttl)`)
- [ ] `infrastructure/storage/MinioDocumentStorageAdapter.java`
- [ ] `infrastructure/config/MinioProperties.java` (`@ConfigurationProperties("app.minio")`)
- [ ] Bucket bootstrap on startup (`@PostConstruct` or `ApplicationRunner`)
- [ ] Integration test with Testcontainers MinIO; assert presigned URL returns the bytes we wrote
- [ ] Commit: `feat(storage): MinIO adapter with streaming put + presigned URL`

## Phase 5 — Upload endpoint
- [ ] `infrastructure/web/DocumentController.upload`
- [ ] Multipart contract: `metadata` (application/json) + `file` (application/pdf), in that order
- [ ] Disable Spring's default multipart resolver (`spring.servlet.multipart.enabled=false`)
- [ ] `infrastructure/web/multipart/MultipartStreamReader` wrapping `commons-fileupload2-jakarta`
- [ ] `application/UploadDocumentUseCase`
- [ ] Object key strategy: `{userId}/{documentId}__{sanitized-name}.pdf`
- [ ] Compensating cleanup: on DB write failure, delete the just-stored object
- [ ] Optional PDF magic-byte sniff at stream start (configurable)
- [ ] Tests: 201 happy path, oversize → 413, non-PDF → 415 (when enforcement on), missing metadata → 400, file-before-metadata → 400
- [ ] Concurrency test: 10 × 100 MB streams under `-Xmx50m`; assert success + heap < 40 MB
- [ ] Commit: `feat(upload): streaming PDF upload to MinIO with persisted metadata`

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
