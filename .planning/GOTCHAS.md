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

## 2026-05-04 — Phase 1

- **Java is not installed on the host.** `/usr/libexec/java_home` returns nothing; `which java` resolves to the macOS stub. `./mvnw` fails with "Unable to locate a Java Runtime." Workaround: run Maven inside the official `maven:3.9-eclipse-temurin-17` Docker image with the host `~/.m2` mounted as cache. The Dockerfile build also uses Maven inside Docker, so this is consistent. If the user installs Temurin 17 later, `mvnw` will work locally.
- **Docker credential helper not on PATH.** `~/.docker/config.json` declares `credsStore: desktop` but `docker-credential-desktop` lives in `/Applications/Docker.app/Contents/Resources/bin/` which isn't on the shell PATH. Symptom: `error getting credentials - err: exec: "docker-credential-desktop": executable file not found in $PATH`. Workaround in scripts: prepend `PATH="$PATH:/Applications/Docker.app/Contents/Resources/bin"` to the docker invocation. A permanent fix is shell-rc-level but the user did not ask for that.
- **The literal `memory: 50M` from the README's compose example is impossible** for a stock Spring Boot 3 app. Heap is 50 MB; the JVM also needs ~30–60 MB for metaspace, code cache, JIT, native, and thread stacks. 256 MB is the empirical minimum. Decision and rationale captured in `ADR-0012`. Heap constraint is preserved via `JAVA_OPTS=-Xmx50m` and proven by the heap-bounded test in `ADR-0007`.
- **`spring-boot-maven-plugin` excludes section** does not silently support arbitrary keys — list each `<exclude>` with `<groupId>` and `<artifactId>` precisely. We exclude both Lombok (already there) and Spring Boot DevTools (added in Phase 1) to keep the runtime image lean.
- **`docker compose --env-file <path>` resolves the path against the shell's CWD,** not the compose file location. Boot the stack from repo root with `--env-file .env` so the example commands in the compose comment block work as written.
- **`bitnami/postgresql:15.4.0` was pulled from Docker Hub** when Bitnami switched to a subscription model in 2024. The byte-identical image is mirrored at `bitnamilegacy/postgresql:15.4.0`. We point the compose file there instead of bumping to a newer minor — preserves the starter's intent. (`bitnami/postgresql:latest` still exists but tracks Postgres 17, which would silently change the runtime.)
- **Smoke test memory profile (Phase 1, 2026-05-04):** with `JAVA_OPTS=-Xmx50m -Xms50m -XX:MaxMetaspaceSize=64m`, the running service consumed **191 MiB / 256 MiB** container memory at idle (post-startup). Confirms ADR-0012's empirical 256 MiB minimum: heap 50 MB + metaspace ~50 MB + native/JIT/threads ~90 MB. Any future bump to deps that pulls more classes will push us past 256 — re-check at the end of Phase 8.
- **Spring Boot 3.4 actuator's `/actuator/info` returns `{}`** by default (no info contributors). To populate it, either set `info.app.*` properties or add `git-commit-id-maven-plugin`. Acceptable for Phase 1; revisit in Phase 8.
- **Springdoc default `/v3/api-docs` returns an empty `paths: {}` JSON** while no `@RestController` is registered. That is correct behavior — the doc reflects live mappings — but worth knowing so we don't chase a phantom bug later.
