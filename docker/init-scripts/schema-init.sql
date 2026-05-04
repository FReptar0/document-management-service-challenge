-- Document Management Service — schema initialization.
-- Mounted at /docker-entrypoint-initdb.d/ and executed by bitnami/postgresql on first
-- volume initialization. Re-run requires `docker compose down -v` to wipe the data volume.
-- Design rationale: see .planning/ADRs/0003-database-schema-design.md.

CREATE SCHEMA IF NOT EXISTS document_schema;
SET search_path = document_schema;

-- pg_trgm powers the trigram GIN index used for case-insensitive ILIKE / similarity
-- search on documents.name (Phase 6 search endpoint).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ===========================================================================
-- documents — one row per uploaded PDF; one MinIO object per row (object_key UNIQUE).
-- ===========================================================================
CREATE TABLE IF NOT EXISTS documents (
    id          UUID          PRIMARY KEY,
    user_id     VARCHAR(128)  NOT NULL,
    name        VARCHAR(512)  NOT NULL,
    object_key  VARCHAR(1024) NOT NULL UNIQUE,
    file_size   BIGINT        NOT NULL CHECK (file_size >= 0),
    file_type   VARCHAR(127)  NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ===========================================================================
-- tags — normalized tag dictionary (lowercase, [a-z0-9_-], 1..64 chars per ADR-0009).
-- BIGSERIAL because tags accumulate forever and we never want to hit int overflow.
-- ===========================================================================
CREATE TABLE IF NOT EXISTS tags (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(64) NOT NULL UNIQUE
);

-- ===========================================================================
-- document_tags — many-to-many join.
-- ON DELETE CASCADE on documents: deleting a document removes its tag links.
-- ON DELETE RESTRICT on tags: never silently drop a tag while documents reference it.
-- ===========================================================================
CREATE TABLE IF NOT EXISTS document_tags (
    document_id UUID   NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    tag_id      BIGINT NOT NULL REFERENCES tags (id)      ON DELETE RESTRICT,
    PRIMARY KEY (document_id, tag_id)
);

-- ===========================================================================
-- Indices tuned for the search endpoint (Phase 6).
-- ===========================================================================

-- Most common query: WHERE user_id = ? ORDER BY created_at DESC.
CREATE INDEX IF NOT EXISTS idx_documents_user_created
    ON documents (user_id, created_at DESC);

-- Case-insensitive substring / similarity search on the document display name.
CREATE INDEX IF NOT EXISTS idx_documents_name_trgm
    ON documents USING gin (name gin_trgm_ops);

-- Tag-driven lookup: "find all documents with tag X". (document_id, tag_id) is the PK
-- and serves the reverse direction.
CREATE INDEX IF NOT EXISTS idx_document_tags_tag_doc
    ON document_tags (tag_id, document_id);
