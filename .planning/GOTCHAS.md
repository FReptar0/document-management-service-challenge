# Gotchas — running learning log

> One bullet per gotcha. Lead with the lesson, then context. Append, never reorder. Date in ISO format. Keep entries terse — this file is read often.

## 2026-05-03 — Phase 0

- **OpenAPI says `application/json` for upload, but a 500 MB PDF in JSON is a non-starter under a 50 MB heap.** Decision: deviate to `multipart/form-data` with a `metadata` JSON part and a `file` binary part. Documented in `ADR-0011`. *Source: `docs/document-management-open-api.yml` lines 13–24.*
- **Spotless is bound to the `check` goal** (`pom.xml` lines 149–155). A non-formatted file will fail `mvn verify` even though there is no explicit `<phase>`. Always run `./mvnw spotless:apply` before commit.
- **JaCoCo plugin declared twice in `pom.xml`** (lines 86–90 and 103–121). First block is dead. Note before any pom edit; dedupe in Phase 1.
- **`docker/docker-compose.yml` ships `{CHANGE_ME}` as literal text, not env interpolation.** First boot will fail until replaced with `${VAR}` references and an `.env` file is present.
- **bitnami/postgresql data dir is `/bitnami/postgresql`**, not `/var/lib/postgresql/data`. Volume bind path matters when reasoning about wipes.
- **Spring's default multipart resolver buffers parts to disk via the servlet container** (Tomcat) once they exceed `file-size-threshold`. Disk-buffered ≠ heap-safe — Tomcat still copies through small heap buffers, and at 50 MB cap with 10 concurrent 500 MB uploads we cannot afford the headroom. Use raw streaming via `commons-fileupload2-jakarta`. See `ADR-0002`.
- **MinIO 8.4.3 is older.** 8.5.x has clearer exception messages and presigned URL parity with newer MinIO server builds. Bump in Phase 1.
- **`schema-init.sql` runs ONLY on first volume initialization.** Re-running `docker compose up` against an existing volume will not re-apply DDL. To pick up schema changes during dev: `docker compose down -v` first. Add a banner to the README dev section.
- **`schema-init.sql` ships with a comment claiming it was sourced from Spring Batch Core** — it was not. Pure noise; delete on rewrite.
- **`spring-boot-devtools` is in runtime classpath** but should never ship in a prod image. Exclude in `spring-boot-maven-plugin` config (Lombok already excluded — follow the same pattern).
- **`PutObjectArgs.stream(in, -1, partSize)` with `partSize=5MB`** lets the MinIO SDK do multipart upload with bounded memory. Validate this with the heap-bounded concurrency test in Phase 9 — do not assume.
- **No `Co-Authored-By:` trailers in any commit or PR body for this repo.** Technical-test submission must read as solo work. Encoded in `CLAUDE.md` §6 and in `~/.claude/projects/.../memory/feedback_no_coauthors.md`.
- **Submission goes to a fresh personal GitHub repo, never to the cloned origin.** The starter template is reused across candidates; PR'ing or pushing to that origin would expose this solution to other interviewees. Encoded in `CLAUDE.md` §6 and in `memory/project_destination_repo.md`.
- **Ports 5432 / 9000 / 9001 verified free 2026-05-03** after the user stopped the conflicting `belvo_postgres` container. `docker ps` empty — challenge stack is clear to boot in Phase 1.
