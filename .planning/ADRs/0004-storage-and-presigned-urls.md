# ADR-0004: MinIO storage, deterministic object keys, 15-min presigned GET URLs

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** storage, minio, security, urls

## Context
Per README, documents are stored in MinIO at `document-bucket/{user}/{doc}.pdf`; the download endpoint returns a temporary URL. We need a key scheme that (a) matches the README's directory illustration in spirit, (b) avoids collisions on duplicate names per user, (c) is stable enough to be referenced by the DB row, and (d) is human-readable in the MinIO console.

## Decision
- **Bucket:** `document-bucket`. Configurable via `app.minio.bucket`. Created on startup if absent.
- **Object key:** `{userId}/{documentId}__{sanitized-name}.pdf` (separator `__` configurable via `app.minio.key.separator`).
- **Sanitization:** lowercase the name, replace whitespace with `-`, strip characters outside `[a-z0-9._-]`, truncate to 200 chars; if the result ends without `.pdf`, append it.
- **Content type:** stored on the object metadata (`application/pdf`).
- **Presigned download URL:** `Method.GET`. Default TTL **900 seconds (15 min)**, configurable via `app.minio.presigned.ttl-seconds`.
- **Uploads:** server-streamed (never presigned PUT) — see `ADR-0002`.
- **Bucket lifecycle:** none for the challenge (no expiration, no replication).
- **Public access:** bucket policy left default (private). Only presigned URLs grant access.
- **Endpoint URL exposed by presigned URLs:** `app.minio.public-endpoint` (defaults to `app.minio.endpoint`). This separation matters in Docker — internally the service speaks to `minio:9000`, but the presigned URL must be reachable from the *client*, typically `http://localhost:9000`.

## Consequences
- **Positive:** Predictable, debuggable keys; safe under concurrent uploads of the same name; download is stateless on the service (URL is signed by MinIO). Console listing groups by user via the `{userId}/` prefix, matching the README's illustration.
- **Negative:** Object key drifts slightly from the README illustration (`{user}/{name}.pdf` vs `{user}/{id}__{name}.pdf`). Documented as a non-functional deviation in the submission README.
- **Risks:** Presigned URL leakage (TTL window). **Mitigation:** short TTL; treat presigned URLs as ephemeral secrets; do not log them; rely on MinIO's audit log if needed.

## Alternatives considered
- **`{userId}/{name}.pdf` literal.** Collisions silently overwrite or fail; both bad.
- **`{userId}/{yyyy}/{mm}/{name}-{uuid}.pdf`.** Tidier in console for very large datasets; out of scope here.
- **Presigned PUT for upload.** Bypasses the service requirement (`ADR-0002`).

## Links
- README §"Upload Endpoint", §"Download Endpoint"
- `docs/minio-local-setup.md`
- `ADR-0002`
- `ADR-0008` (config: where the key strategy is exposed)
