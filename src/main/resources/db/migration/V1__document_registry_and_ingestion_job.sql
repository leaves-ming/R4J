CREATE TABLE IF NOT EXISTS document_registry (
    collection_id VARCHAR(255) NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_path VARCHAR(1024) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(255) NOT NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    error_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ready_at TIMESTAMPTZ,
    PRIMARY KEY (collection_id, document_id)
);

CREATE INDEX IF NOT EXISTS idx_document_registry_status
    ON document_registry (collection_id, status);

CREATE TABLE IF NOT EXISTS ingestion_job (
    job_id UUID PRIMARY KEY,
    collection_id VARCHAR(255) NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    force_reingest BOOLEAN NOT NULL DEFAULT FALSE,
    requested_chunk_size INTEGER NOT NULL,
    requested_chunk_overlap INTEGER NOT NULL,
    error_reason TEXT,
    trace_id VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ingestion_job_collection_document
    ON ingestion_job (collection_id, document_id);
