# ADR-0011: Upload accepts multipart/form-data (deviates from OpenAPI's application/json)

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** api, contract, deviation, multipart

## Context
`docs/document-management-open-api.yml` (lines 13–24) declares the upload request body as `application/json` carrying `{ user, name, tags[] }` — i.e., metadata only, no binary part. Yet README §"Upload Endpoint" requires *"uploading a PDF document along with the following metadata"* and stores the PDF in MinIO. Two readings reconcile the contradiction:

1. The OpenAPI is incomplete and the intended transport is `multipart/form-data`.
2. The PDF is meant to be uploaded out-of-band (e.g., a presigned PUT) and the JSON carries metadata only.

A 500 MB PDF carried in JSON (base64-encoded) inflates to ~667 MB and would have to be parsed in memory before it can be streamed anywhere — incompatible with the 50 MB heap (`ADR-0002`). Reading 2 contradicts *"the service must handle PDF uploads"* and *"the uploaded PDF should be stored in [the] bucket"*.

## Decision
**Treat the OpenAPI as incomplete and deviate.** The upload endpoint accepts `multipart/form-data` with two parts in this order:

| Part name | Content-Type | Required | Notes |
|---|---|---|---|
| `metadata` | `application/json` | Yes (must come first) | Body matches the existing `UploadDocument` schema |
| `file` | `application/pdf` | Yes | The PDF binary stream |

### Behavioral contract
- **Order matters.** `metadata` must be parsed before `file` so we can validate before consuming bytes. If `file` arrives first → 400 `multipart-order-violation`.
- **Maximum total request size:** 500 MB. Above that → 413. Configurable via `app.upload.max-size-bytes`.
- **PDF magic-byte sniff:** read first 5 bytes of `file`; require `%PDF-` when `app.upload.enforce-pdf-magic=true` (default). On mismatch → 415.
- **Idempotency:** none. A successful upload always produces a fresh `documentId`. Clients implement deduplication if needed.
- **Legacy JSON-only contract is not supported.** The local copy of `docs/document-management-open-api.yml` is patched to reflect the multipart contract; the deviation is also documented in the submission `README.md` "Deviations" section.

### Why declare order?
Streaming parse from the raw HTTP request requires reading parts sequentially. We *could* buffer `file` to disk if it arrives first and replay it after `metadata`, but that costs heap and disk and complicates failure semantics. Documenting the order is cheaper and a normal pattern in multipart APIs (e.g., AWS S3's POST policy, GitHub's release-asset upload).

## Consequences
- **Positive:** Honors the README's spirit and the memory constraint; standard HTTP idiom; no client-side base64 dance; smaller responses on errors (no echo of binary).
- **Negative:** Reviewers comparing strictly to the original OpenAPI will see a difference. Mitigated by:
  1. Patching the local OpenAPI YAML to match what the service actually accepts.
  2. Calling out the deviation in the submission README and this ADR.
  3. Keeping the `UploadDocument` JSON schema unchanged — it is now the schema of the `metadata` part rather than the whole body.
- **Risks:** Reviewer scoring may dock points for spec deviation. **Accepted:** the alternative (JSON-only with base64) violates a hard constraint and would not run.

## Alternatives considered
- **JSON only with base64-encoded PDF.** OOMs at 500 MB; demonstrably impossible under `-Xmx50m`.
- **Split into two endpoints (metadata `POST`, then `PUT` to a presigned URL).** Changes the API surface materially and contradicts *"the service must handle PDF uploads"*.
- **Use the JSON contract as-is and skip storing the PDF.** Does not satisfy README; non-starter.
- **Proprietary chunked upload (tus / resumable).** Significant API surface for unclear gain; out of scope.

## Links
- README §"Upload Endpoint"
- `docs/document-management-open-api.yml` (will be patched in Phase 5 to reflect the multipart contract)
- `ADR-0002` (streaming requires multipart)
