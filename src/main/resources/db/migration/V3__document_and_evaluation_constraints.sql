ALTER TABLE document_registry
    ADD CONSTRAINT chk_document_registry_status
        CHECK (status IN ('RECEIVED', 'PROCESSING', 'READY', 'FAILED'));

ALTER TABLE document_registry
    ADD CONSTRAINT chk_document_registry_ready_at
        CHECK ((status = 'READY' AND ready_at IS NOT NULL) OR (status <> 'READY'));

CREATE INDEX IF NOT EXISTS idx_document_registry_ready_visibility
    ON document_registry (collection_id, status, ready_at);

ALTER TABLE ingestion_job
    ADD CONSTRAINT chk_ingestion_job_status
        CHECK (status IN ('RECEIVED', 'PROCESSING', 'READY', 'FAILED', 'SKIPPED'));

CREATE INDEX IF NOT EXISTS idx_ingestion_job_trace_id
    ON ingestion_job (trace_id);

CREATE INDEX IF NOT EXISTS idx_evaluation_run_collection_created_at
    ON evaluation_run (collection_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_evaluation_case_result_run_query
    ON evaluation_case_result (run_id, query_index);
