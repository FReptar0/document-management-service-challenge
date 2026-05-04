# Architecture Decision Records (ADRs)

This directory captures every non-trivial design decision for the Document Management Service Challenge. Each ADR is a single Markdown file with `Status`, `Date`, `Context`, `Decision`, `Consequences`, `Alternatives considered`, and `Links`. ADRs are immutable once accepted: changes are introduced by adding a new ADR that supersedes the old one (mark old `Superseded by ADR-XXXX`).

## Index

|  ID  |                                        Title                                        |  Status  |
|------|-------------------------------------------------------------------------------------|----------|
| 0001 | Hexagonal-lite architecture                                                         | Accepted |
| 0002 | Streaming uploads — bypass Spring multipart, use commons-fileupload2 on raw request | Accepted |
| 0003 | Normalized schema with documents/tags/document_tags + targeted indices              | Accepted |
| 0004 | MinIO storage, deterministic object keys, 15-min presigned GET URLs                 | Accepted |
| 0005 | Synchronous request lifecycle, sized Tomcat thread pool, no async                   | Accepted |
| 0006 | Global @RestControllerAdvice with RFC 7807 ProblemDetail                            | Accepted |
| 0007 | Layered tests with Testcontainers and a heap-bounded concurrency test               | Accepted |
| 0008 | Externalized config via env vars, layered application.yml, no secrets in source     | Accepted |
| 0009 | Jakarta Bean Validation + dedicated DTOs at the web edge                            | Accepted |
| 0010 | Structured logging with MDC, Actuator health, Springdoc OpenAPI UI                  | Accepted |
| 0011 | Upload accepts multipart/form-data (deviates from OpenAPI's application/json)       | Accepted |
| 0012 | Container memory limit set above heap to allow JVM startup overhead                 | Accepted |

## Conventions

- Filename: `XXXX-kebab-case-title.md` where `XXXX` is the zero-padded ID.
- One decision per ADR. If a single discussion produces two truly orthogonal decisions, write two ADRs.
- Status: `Proposed` → `Accepted` → `Deprecated` / `Superseded`. Never delete.
- Tag with relevant keywords for grep-ability (`memory`, `upload`, `schema`, etc.).
- Cross-link related ADRs in the `Links` section.

## Template

See `template.md` in this directory.
