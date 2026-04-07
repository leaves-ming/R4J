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
