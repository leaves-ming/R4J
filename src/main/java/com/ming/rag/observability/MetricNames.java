package com.ming.rag.observability;

public final class MetricNames {

    public static final String INGESTION_TOTAL = "rag.ingestion.total";
    public static final String INGESTION_LATENCY_MS = "rag.ingestion_latency_ms";
    public static final String INGESTION_READY_TOTAL = "rag.ingestion.ready_total";
    public static final String INGESTION_FAILURE_TOTAL = "rag.ingestion_failure_total";
    public static final String INGESTION_SKIPPED_TOTAL = "rag.ingestion.skipped_total";
    public static final String INGESTION_COMPENSATION_TOTAL = "rag.ingestion_compensation_total";
    public static final String INGESTION_CHUNK_COUNT = "rag.ingestion_chunk_count";

    public static final String QUERY_TOTAL = "rag.query_total";
    public static final String QUERY_FAILURE_TOTAL = "rag.query_failure_total";
    public static final String QUERY_LATENCY_MS = "rag.query_latency_ms";
    public static final String DENSE_LATENCY_MS = "rag.dense_latency_ms";
    public static final String SPARSE_LATENCY_MS = "rag.sparse_latency_ms";
    public static final String RERANK_LATENCY_MS = "rag.rerank_latency_ms";
    public static final String LLM_LATENCY_MS = "rag.llm_latency_ms";
    public static final String RETRIEVAL_FALLBACK_TOTAL = "rag.retrieval_fallback_total";
    public static final String QUERY_RESPONSE_TOTAL = "rag.query_response_total";

    public static final String EVALUATION_RUN_TOTAL = "rag.evaluation_run_total";
    public static final String EVALUATION_CASE_TOTAL = "rag.evaluation_case_total";
    public static final String EVALUATION_LATENCY_MS = "rag.evaluation_latency_ms";
    public static final String EVALUATION_METRIC_VALUE = "rag.evaluation_metric_value";

    private MetricNames() {
    }
}
