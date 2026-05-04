# ADR-0009: Jakarta Bean Validation + dedicated DTOs at the web edge

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** validation, dto, api, security

## Context
README §"Additional Considerations": *"Validations on models and DTOs (e.g., non-null constraints)."* We also need to lock down inputs that feed into object keys, SQL queries, and tag uniqueness — bad inputs break invariants in `ADR-0003` and `ADR-0004`.

## Decision
- Add `org.springframework.boot:spring-boot-starter-validation`.
- Request DTOs annotated with Jakarta Bean Validation; responses are plain DTOs (no validation needed).

### DTO specifics

```
infrastructure/web/dto/
├── UploadMetadataRequest.java   { user (NotBlank, ≤128, no path sep), name (NotBlank, ≤255, no path sep), tags (Set, size 0..32, each @ValidTag) }
├── DocumentSearchFiltersRequest.java   { user?, name?, tags? (Set, each @ValidTag) }
├── DocumentResponse.java        { id, user, name, tags, size, type, createdAt }
├── PaginatedDocumentSearchResponse.java
├── MetadataResponse.java
└── DocumentDownloadUrlResponse.java   { url }
```

### Tag normalization rules (`@ValidTag`)
- Trim leading/trailing whitespace.
- Lowercase.
- Allowed charset: `[a-z0-9_-]`.
- Length: 1..64.
- Normalization is applied **before** validation, so a tag `" Finance "` becomes `"finance"` and is then validated. Idempotent at the DB UPSERT.

### Document name rules
- `@NotBlank`, `@Size(max=255)`.
- No path separators (`/`, `\`).
- Characters outside `[A-Za-z0-9._\- ]` allowed in storage *only after sanitization* into the object key (`ADR-0004`); the DB stores the original (post-trim) name as provided.

### Search bounds
- `page >= 0`, `1 <= size <= 100` (prevents query plan blow-up).

### User identifier
- `@NotBlank`, `@Size(max=128)`, no path separators (we embed in object keys).

### Upload binary part
- Magic-byte sniff at the first 5 bytes (`%PDF-`); configurable via `app.upload.enforce-pdf-magic`. When enforcement is off (e.g., negative tests), we still record the actual content-type but skip the gate.

Validation errors flow through the global advice (`ADR-0006`) as RFC 7807 with a `errors[]` field (Spring's standard).

## Consequences
- **Positive:** Sanitized inputs at the edge; consistent error UX; no spurious DB hits or storage writes on bad input; tag uniqueness becomes self-evident.
- **Negative:** Boilerplate DTO classes. Each DTO needs a Lombok `@Builder` or hand-written constructor. Accepted.

## Alternatives considered
- **Validate at the use-case layer with manual checks.** More code, less standard, no Spring integration.
- **Reuse JPA entities as DTOs.** Couples the wire to the schema and leaks framework annotations.

## Links
- README §"Additional Considerations"
- `ADR-0003` (DB schema feeds tag UNIQUE)
- `ADR-0004` (object key sanitization)
- `ADR-0006` (error advice)
