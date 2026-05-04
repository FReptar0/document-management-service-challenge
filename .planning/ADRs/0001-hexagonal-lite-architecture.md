# ADR-0001: Hexagonal-lite architecture

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** architecture, structure, packaging

## Context

Senior-level evaluation. README §"Design Patterns and Best Practices" explicitly calls out *"design patterns (Controller-Service-Repository or Hexagonal Architecture) and adherence to SOLID"*. The system has clear external dependencies (HTTP, PostgreSQL, MinIO) with very different lifecycles and failure modes — they are obvious adapter boundaries.

## Decision

Adopt **hexagonal-lite** (ports & adapters) with three top-level packages and **no framework leakage into the domain or application layers**.

```
com.clara.ops.challenge.dms
├── domain/                        # entities, value objects, domain exceptions
│   ├── port/                      # driven port interfaces (owned by the domain)
│   └── exception/
├── application/                   # use cases (orchestrators); pure Java + ports
└── infrastructure/
    ├── web/                       # controllers, DTOs, advice, filters
    ├── persistence/               # JPA entities, Spring Data repos, adapters
    ├── storage/                   # MinIO adapter
    └── config/                    # @Configuration, @ConfigurationProperties, beans
```

Ground rules:
- `domain/` and `application/` import nothing from `org.springframework.*`, `io.minio.*`, `jakarta.persistence.*`, or any other framework.
- DTOs live in `infrastructure/web`. Domain types are not serialized over the wire.
- JPA entities live in `infrastructure/persistence`. Domain entities are not annotated with JPA.
- Tests of `application` use plain Mockito mocks of ports; tests of adapters use Testcontainers (real Postgres, real MinIO).
- One port per external concern, no premature interfaces inside the domain.

## Consequences

- **Positive:** Clear seams; replaceable adapters (could swap MinIO for AWS S3 by writing one class); use-case logic is unit-testable without Spring; matches the evaluation criteria explicitly; supports the streaming constraints in `ADR-0002` cleanly because the storage port is defined by what the *use case* needs (`InputStream` in, presigned URL out), not by what MinIO happens to expose.
- **Negative:** More classes than a flat layered project. Roughly 2× the boilerplate of plain Controller-Service-Repository. Mappers between domain and JPA / DTO add files.
- **Risks:** Over-abstraction. **Mitigation:** "lite" — only the boundaries that have a real reason to vary get a port. No `interface DocumentService` that has only one implementation.

## Alternatives considered

- **Plain layered (Controller-Service-Repository).** Simpler, but JPA leaks into services and the boundary between business logic and IO blurs. The streaming upload pipeline has enough subtlety that mixing it with persistence concerns at the same layer is asking for trouble.
- **Full DDD with aggregates / domain events.** Overkill for a 3-endpoint service. We would spend evaluation time on aggregate-root debates instead of the actual hard problem (streaming).

## Links

- README §"Design Patterns and Best Practices"
- `ADR-0002` (streaming upload — adapter shape)
- `ADR-0007` (testing strategy — port mocks vs adapter integration tests)

