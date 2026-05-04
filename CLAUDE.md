# CLAUDE.md — Document Management Service Challenge

> Operating manual for Claude Code while working on this repository. Read on every session start. When a decision changes, update the relevant ADR first, then this file's pointers.

## 1. Mission

Build a backend service that ingests, indexes, and serves PDFs up to **500 MB** under a hard **50 MB JVM heap limit**, with **10 concurrent uploads**, against PostgreSQL + MinIO. The challenge target is a senior-level evaluation; the goal is a clean, observable, production-shaped solution — not a maximal one.

## 2. Hard constraints (non-negotiable)

| Constraint | Value | Source |
|---|---|---|
| JVM heap | `-Xmx50m -Xms50m` | README §"Memory Limitation" |
| Max file size | 500 MB | README §"Upload Endpoint" |
| Concurrent uploads | 10 (each up to 500 MB) | README §"Concurrent Uploads" |
| Java | 17 (Java 8+ acceptable) | pom.xml |
| Spring Boot | 3.4.3 | pom.xml |
| Storage backend | MinIO (S3-compatible) | README + `docs/minio-local-setup.md` |
| Bucket layout | `document-bucket/{user}/...` | README §"Upload Endpoint" |
| DB | PostgreSQL (bitnami/postgresql:15.4.0) | `docker/docker-compose.yml` |
| Stack startup | `docker compose up --build` | README §"Implementation Instructions" |
| Schema script | `docker/init-scripts/schema-init.sql` | README §"Implementation Instructions" |
| Config externalization | All sensitive values via env vars | README §"Note" |

## 3. Performance budget (derived from §2)

With 50 MB heap and 10 concurrent uploads:
- **Per-upload steady-state buffer:** ≤ 256 KB on the parsing side.
- **MinIO multipart part size:** 5 MB (S3 minimum). Stream a single part at a time per upload — never accumulate.
- **JPA fetch size:** small (`hibernate.jdbc.fetch_size=20`); never load whole tables.
- **Banned on the upload path:** `byte[]`, `MultipartFile.getBytes()`, `IOUtils.toByteArray`, `Files.readAllBytes`.

If a change touches the upload path, confirm the heap math holds before merging.

## 4. Architectural ground rules

- **Hexagonal-lite** (`domain/`, `application/`, `infrastructure/`). See ADR-0001.
- **Streaming everywhere on the hot path:** `InputStream` in, `InputStream` out. Never materialize a PDF in memory. See ADR-0002.
- **Domain ↔ DTO:** separate request/response DTOs from JPA entities; map at the boundary.
- **No business logic in controllers.** Controllers translate HTTP to use case calls.
- **Adapters are replaceable:** storage and persistence behind ports; tests use real infra via Testcontainers, not mocks for adapter tests.
- **No Spring annotations in `domain/` or `application/`.** Wiring happens in `infrastructure/config/`.

## 5. Code style

- `./mvnw spotless:apply` before every commit. Spotless is bound to `check` in pom.xml (lines 122–157) and **will fail the build** if not applied.
- Google Java Format, javadoc reflowed, unused imports removed.
- Lombok permitted only at boundaries (DTOs, JPA entities). Domain types are plain Java with explicit constructors and `equals`/`hashCode` where needed.
- No emojis in code, log messages, or commit messages.
- Comments only for non-obvious "why". File-level Javadoc only on public ports/use cases.

## 6. Commit hygiene & repository policy

- **Conventional Commits** (`feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`).
- One logical change per commit. Format → test → commit.
- Never `git push --force` on shared branches.
- Never `--no-verify`. Never amend a pushed commit.
- One commit per ADR-significant decision; reference the ADR ID in the commit body.
- **No `Co-Authored-By:` trailers and no AI attribution** in commit messages or PR bodies. This is a technical-test submission — commits must read as solo authored work. Overrides the default Claude Code commit-template footer.
- **Submission destination: a fresh personal GitHub repo** owned by the operator. The starter was cloned from a template reused across candidates; **never** push or open a PR against that origin. When shipping, preserve the full incremental commit history (no squashing, no `reset --hard`).

## 7. Quality gate per commit

Run before every commit (in order):
1. `./mvnw spotless:apply`
2. `./mvnw -q test` (or at minimum the touched module's tests)
3. `git diff --stat` — confirm scope matches intent
4. Update relevant ADR if a decision changed
5. Append to `.planning/GOTCHAS.md` if you learned something non-obvious

## 8. Workflow rules for Claude Code in this repo

- **Read `.planning/ADRs/` before any non-trivial change.** If your plan contradicts an ADR, write a new ADR superseding it before coding.
- **Update `.planning/TODOS.md`** when a task starts or finishes — it is the source of truth for in-flight work.
- **Append to `.planning/GOTCHAS.md`** every time you hit a surprise (a misleading docs link, a Spring default that bit you, a MinIO quirk). One bullet, lead with the lesson, include date and source.
- **Use `Agent` (Explore subagent)** when scanning beyond ~3 files to keep main-context lean.
- **Atomic commits** — break work into the smallest meaningful unit.
- **Verify Docker behavior locally** before claiming a feature works: `docker compose up --build`, run real upload via `curl`/Postman, check MinIO console + DB rows. Type checking and unit tests verify code correctness, not feature correctness.
- **Match action scope to user request.** Don't refactor surrounding code while fixing a bug. Don't add a feature flag for a one-off change.
- **Parallel tool calls** wherever possible (independent reads/writes, multiple greps).

## 9. Anti-patterns specifically banned for this challenge

- `MultipartFile.getBytes()` / `MultipartFile.transferTo(...)` to anywhere except a streamed pipe.
- `Files.readAllBytes` / `IOUtils.toByteArray` on uploaded payloads.
- `@RequestPart MultipartFile` without configuring a streaming-aware multipart resolver. Spring's default buffers parts via the servlet container; on the 50 MB budget the safer path is raw streaming — see ADR-0002.
- Storing PDF bytes in PostgreSQL (`bytea`).
- Returning the actual PDF stream from the download endpoint — the contract is **a presigned URL** (see OpenAPI `DocumentDownloadUrl`).
- Hardcoded credentials. All secrets via env vars (see ADR-0008).
- Eager fetching of `documents.tags` in lists (N+1 risk on search).
- Long-lived presigned URLs. Default ≤ 15 min.
- Killing the JVM with `-Xmx512m` to "make tests pass" — the constraint is the point.

## 10. Reference index

- `README.md` — challenge spec (immutable except where ADR-0011 explicitly deviates)
- `docs/document-management-open-api.yml` — API contract (will be patched per ADR-0011)
- `docs/minio-local-setup.md` — MinIO operator guide
- `docker/docker-compose.yml` — local stack
- `docker/init-scripts/schema-init.sql` — DB schema (we own this)
- `.planning/ANALYSIS.md` — codebase + requirements relación
- `.planning/ROADMAP.md` — phased plan
- `.planning/TODOS.md` — actionable backlog
- `.planning/GOTCHAS.md` — running learning log
- `.planning/ADRs/` — decisions
- `CLAUDE.md` — this file

## 11. Open contract questions (resolved as we go)

| Q | Decision | Where |
|---|---|---|
| OpenAPI says upload is `application/json` — but a 500 MB PDF in JSON is impractical. Do we deviate to `multipart/form-data`? | **Yes**, deviate | ADR-0011 |
| Should document name be unique per user? | **No** — UUID id disambiguates; key is `{user}/{id}__{name}.pdf` | ADR-0004 |
| Tag normalization (case, trim, length, charset)? | lower-case, trim, max 64 chars, `[a-z0-9_-]+` | ADR-0009 |
| Presigned URL TTL? | 15 min, configurable | ADR-0004 |
| Bucket name? | `document-bucket`, configurable | ADR-0004 |

## 12. Submission checklist (final)

- [ ] All endpoints implemented per OpenAPI (with multipart deviation per ADR-0011)
- [ ] `docker compose up --build` boots the full stack including the service
- [ ] Memory limit honored at runtime (verify with `docker stats`)
- [ ] Schema script idempotent and complete in `docker/init-scripts/schema-init.sql`
- [ ] `./mvnw verify` green (tests + jacoco + spotless)
- [ ] README updated with run / test / verify instructions, env-var matrix, design notes, deviations
- [ ] Postman collection exported to `docs/postman/`
- [ ] Clean Conventional Commits history
- [ ] Pushed to a **fresh personal** GitHub repo (never to the cloned template — it is reused across candidates), full incremental commit history preserved, URL ready to share
