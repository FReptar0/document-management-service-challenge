# ADR-0003: Normalized schema with documents/tags/document_tags + targeted indices

- **Status:** Accepted
- **Date:** 2026-05-03
- **Tags:** persistence, schema, indexing, postgres

## Context
README §"Database Schema and Indexing" explicitly highlights *"the efficiency of your database schema, including the creation of indices and the management of multiple tags per document."* Documents have many tags; tags are reused across documents; search filters include any-of-tags semantics. PostgreSQL 15.4 (bitnami) is the runtime.

Two main shapes considered:
1. **Array column** — `documents.tags TEXT[]` with a GIN index. Fast for `tags @> ARRAY['x']`; JPA mapping requires custom converters; tag uniqueness/normalization is application-side.
2. **Normalized junction** — `documents`, `tags`, `document_tags`. Verbose but standard SQL, native JPA `@ManyToMany`, easy reporting/analytics, easy uniqueness.

## Decision
Use the **normalized junction model**. PostgreSQL DDL (final form will live in `docker/init-scripts/schema-init.sql`):

```sql
CREATE SCHEMA IF NOT EXISTS document_schema;
SET search_path = document_schema;

CREATE EXTENSION IF NOT EXISTS pg_trgm;  -- for ILIKE / trigram search on name

CREATE TABLE documents (
  id           UUID PRIMARY KEY,
  user_id      VARCHAR(128)  NOT NULL,
  name         VARCHAR(512)  NOT NULL,
  object_key   VARCHAR(1024) NOT NULL UNIQUE,
  file_size    BIGINT        NOT NULL CHECK (file_size >= 0),
  file_type    VARCHAR(127)  NOT NULL,
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE tags (
  id   BIGSERIAL PRIMARY KEY,
  name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE document_tags (
  document_id UUID   NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  tag_id      BIGINT NOT NULL REFERENCES tags(id)      ON DELETE RESTRICT,
  PRIMARY KEY (document_id, tag_id)
);

-- Indices tuned for the search endpoint
CREATE INDEX idx_documents_user_created ON documents (user_id, created_at DESC);
CREATE INDEX idx_documents_name_trgm    ON documents USING gin (name gin_trgm_ops);
CREATE INDEX idx_document_tags_tag_doc  ON document_tags (tag_id, document_id);
-- (document_id, tag_id) is the PK and serves the reverse direction.
```

Notes:
- `id UUID` — generated app-side (`UUID.randomUUID()`); avoids a round-trip and makes object keys collision-proof.
- `object_key UNIQUE` — invariant: one DB row ↔ one MinIO object.
- `idx_documents_user_created` — directly serves the most common query `WHERE user_id = ? ORDER BY created_at DESC`.
- `pg_trgm` GIN — supports case-insensitive substring search on `name` without table scans. Modest index size at the scale we target.
- `(tag_id, document_id)` index — supports tag-driven search "find all docs with tag X" via merge/intersection.
- Cascade delete from `documents` to `document_tags` keeps consistency on document removal (out of scope for this challenge but cheap to set now).
- `tag_id` is `RESTRICT` on delete — tags shouldn't disappear under live documents.

Tag insertion (concurrent-safe upsert):

```sql
INSERT INTO tags (name) VALUES (?)
ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
RETURNING id;
```

(`DO NOTHING` does not return the existing row's id; we use `DO UPDATE` of the same value to force a returned row.)

## Consequences
- **Positive:** Standard SQL; idiomatic JPA; rich indexing options; tag reuse is storage-efficient (one tag string, many references). Reads scale with indices, not table size.
- **Negative:** More joins on read. Mitigated by indices; for our scale the planner picks index-only scans for tag-filtered queries.
- **Risks:** ManyToMany is easy to misconfigure in JPA (cascade and orphan removal pitfalls). **Mitigation:** Hibernate `@ManyToMany` declared on the owning side `DocumentEntity`; tags fetched LAZY; mapping covered by a slice test.

## Alternatives considered
- **`tags TEXT[] + GIN`.** Fewer joins; loses standard JPA mapping; tag uniqueness enforced application-side; harder to extend (tag color, tag created_at, etc.) — rejected for evaluator-friendliness and extensibility.
- **`JSONB tags`.** Same downsides plus typing concerns and worse query plans.
- **Single denormalized table.** Smallest, but no path to extension and tag analytics become a manual aggregation.

## Links
- README §"Schema Management"
- `docker/init-scripts/schema-init.sql` (this ADR's output)
- `ADR-0009` (validation: tag normalization rules feed the UNIQUE constraint)
