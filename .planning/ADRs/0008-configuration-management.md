# ADR-0008: Externalized config via env vars, layered application.yml, no secrets in source

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** config, security, twelve-factor

## Context

README §"Note": *"All configurations (database credentials, MinIO/S3 settings, etc.) must be externalized using environment variables and configuration files. Avoid hardcoding sensitive information in the source code."*

## Decision

- Single `application.yml` with sensible defaults; secrets and host/port via env vars (`${VAR:default}`).
- `@ConfigurationProperties` classes per concern: `MinioProperties`, `UploadProperties`, `PresignedUrlProperties`. DataSource and JPA configured via Spring Boot's standard properties.
- `application-test.yml` activates a `test` profile with random ports and `tc:postgresql:15.4`/Testcontainers JDBC URLs.
- `.env.example` committed; `.env` gitignored.
- Docker Compose uses `${VAR}` interpolation, sourcing from `.env` or shell.

### Required env vars

|              Var               |                           Purpose                           |       Default in dev        |
|--------------------------------|-------------------------------------------------------------|-----------------------------|
| `POSTGRESQL_USERNAME`          | Postgres user                                               | `dms`                       |
| `POSTGRESQL_PASSWORD`          | Postgres password                                           | (required, no default)      |
| `POSTGRESQL_DATABASE`          | DB name                                                     | `challenge`                 |
| `POSTGRESQL_POSTGRES_PASSWORD` | bitnami `postgres` superuser pwd                            | (required)                  |
| `POSTGRESQL_HOST`              | DB host                                                     | `postgresql` (compose name) |
| `POSTGRESQL_PORT`              | DB port                                                     | `5432`                      |
| `MINIO_ENDPOINT`               | MinIO internal endpoint (service ↔ MinIO)                   | `http://minio:9000`         |
| `MINIO_PUBLIC_ENDPOINT`        | MinIO endpoint baked into presigned URLs (client-reachable) | `http://localhost:9000`     |
| `MINIO_ROOT_USER`              | MinIO root                                                  | (required)                  |
| `MINIO_ROOT_PASSWORD`          | MinIO root password                                         | (required)                  |
| `MINIO_ACCESS_KEY`             | App access key                                              | falls back to root user     |
| `MINIO_SECRET_KEY`             | App secret key                                              | falls back to root password |
| `MINIO_BUCKET`                 | Bucket name                                                 | `document-bucket`           |
| `APP_PRESIGNED_TTL_SECONDS`    | Presigned URL TTL                                           | `900`                       |
| `APP_UPLOAD_MAX_SIZE_BYTES`    | Upload hard cap                                             | `524288000` (500 MB)        |
| `APP_UPLOAD_ENFORCE_PDF_MAGIC` | Reject non-PDF magic bytes                                  | `true`                      |
| `JAVA_OPTS`                    | JVM args for service container                              | `-Xmx50m -Xms50m`           |

`MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` are kept distinct from root credentials so an evaluator can rotate the app's keys without recreating MinIO admin. Defaults fall back to root for low-friction first run.

## Consequences

- **Positive:** Twelve-factor-compliant; safe to commit; portable across envs; Testcontainers profile drops in cleanly.
- **Negative:** A small `.env.example` to maintain. Reviewers must `cp .env.example .env` before first boot — documented prominently in README.

## Alternatives considered

- **Vault / Doppler / external secret store.** Overkill; not in scope.
- **Profile-per-environment YAMLs (`application-dev.yml`, `application-prod.yml`).** Encourages drift; we collapse to base + `test`.

## Links

- README §"Note" (config externalization)
- `ADR-0004` (MinIO settings)

