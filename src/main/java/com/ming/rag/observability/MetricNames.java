package com.ming.rag.observability;

public final class MetricNames {

    public static final String INGESTION_REQUESTS = "rag.ingestion.requests";
    public static final String QUERY_REQUESTS = "rag.query.requests";
    public static final String EVALUATION_REQUESTS = "rag.evaluation.requests";
    public static final String INGESTION_DURATION = "rag.ingestion.duration";
    public static final String QUERY_DURATION = "rag.query.duration";
    public static final String EVALUATION_DURATION = "rag.evaluation.duration";

    private MetricNames() {
    }
}
