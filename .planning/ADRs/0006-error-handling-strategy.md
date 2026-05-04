# ADR-0006: Global @RestControllerAdvice with RFC 7807 ProblemDetail

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** errors, api, dx, observability

## Context
Consistent error contract is part of the code-quality evaluation. Spring 6 ships `ProblemDetail` (RFC 7807) out of the box, and the JSON shape it produces is recognizable to any modern HTTP client.

## Decision
A single `@RestControllerAdvice` (`GlobalExceptionHandler`) translating exceptions to `ProblemDetail`:

| Exception | HTTP | Title | Detail |
|---|---|---|---|
| `MethodArgumentNotValidException`, `ConstraintViolationException` | 400 | Validation failed | field-level list |
| `MultipartOrderViolationException` (custom) | 400 | Multipart order violated | "metadata must precede file" |
| `MultipartMissingPartException` (custom) | 400 | Missing part | name of missing part |
| `DocumentNotFoundException` (domain) | 404 | Document not found | id |
| `FileSizeLimitExceededException` (commons-fileupload2) | 413 | Payload too large | configured limit |
| `UnsupportedMediaTypeException` (custom for non-PDF when enforced) | 415 | Unsupported media type | actual content-type |
| `MinioStorageException` (wrapper) / `MinioException` | 502 | Storage backend error | sanitized message |
| `DataAccessException`, `JpaSystemException` | 503 | Database unavailable | sanitized message |
| `Exception` (catch-all) | 500 | Internal error | "see logs (requestId=…)" |

Error envelope:

```json
{
  "type": "about:blank",
  "title": "Document not found",
  "status": 404,
  "detail": "No document with id 9f8b...",
  "instance": "/document-management/download/9f8b...",
  "requestId": "8c1a..."
}
```

`requestId` is sourced from the MDC populated by `RequestIdFilter` (see `ADR-0010`). Stack traces are never returned to the client; they are logged at `ERROR` with the same `requestId`.

Domain exceptions live in `domain/exception/`. Web translation lives in `infrastructure/web/error/`. The advice does not catch business control flow — domain code throws explicit exceptions with semantic types, never raw `RuntimeException`.

## Consequences
- **Positive:** Industry-standard, machine-readable, debuggable. `requestId` makes log correlation trivial.
- **Negative:** A handful of small exception classes. Boilerplate but explicit and grep-friendly.

## Alternatives considered
- **Custom JSON error envelope.** Non-standard; reviewers must learn it.
- **Per-controller `@ExceptionHandler`.** Duplicated logic; drift risk.

## Links
- RFC 7807
- Spring 6 `ProblemDetail`
- `ADR-0010` (MDC `requestId`)
