package com.ming.rag.observability;

public final class MetricNames {

    public static final String INGESTION_REQUESTS = "rag.ingestion.requests";
    public static final String QUERY_REQUESTS = "rag.query.requests";
    public static final String QUERY_FAILED = "rag.query.failed";
    public static final String QUERY_FALLBACK = "rag.query.fallback";
    public static final String EVALUATION_REQUESTS = "rag.evaluation.requests";
    public static final String INGESTION_DURATION = "rag.ingestion.duration";
    public static final String QUERY_DURATION = "rag.query.duration";
    public static final String EVALUATION_DURATION = "rag.evaluation.duration";
    public static final String INGESTION_READY = "rag.ingestion.ready";
    public static final String INGESTION_FAILED = "rag.ingestion.failed";
    public static final String INGESTION_SKIPPED = "rag.ingestion.skipped";

    private MetricNames() {
    }
}
