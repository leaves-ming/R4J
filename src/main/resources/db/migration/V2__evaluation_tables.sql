CREATE TABLE IF NOT EXISTS evaluation_run (
    run_id UUID PRIMARY KEY,
    evaluator_name VARCHAR(255) NOT NULL,
    test_set_path VARCHAR(1024) NOT NULL,
    collection_id VARCHAR(255) NOT NULL,
    aggregate_metrics_json JSONB NOT NULL,
    total_elapsed_ms DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS evaluation_case_result (
    run_id UUID NOT NULL,
    query_index INTEGER NOT NULL,
    query TEXT NOT NULL,
    retrieved_top_k_chunk_ids_json JSONB NOT NULL,
    generated_answer TEXT,
    metrics_json JSONB NOT NULL,
    elapsed_ms DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (run_id, query_index),
    CONSTRAINT fk_evaluation_case_result_run
        FOREIGN KEY (run_id)
        REFERENCES evaluation_run (run_id)
        ON DELETE CASCADE
);
