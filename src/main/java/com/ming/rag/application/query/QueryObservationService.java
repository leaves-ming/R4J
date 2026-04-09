package com.ming.rag.application.query;

import com.ming.rag.observability.MetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QueryObservationService {

    private static final Logger log = LoggerFactory.getLogger(QueryObservationService.class);

    private final MeterRegistry meterRegistry;

    public QueryObservationService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void onRetrievalStarted(String traceId, String collectionId) {
        meterRegistry.counter(MetricNames.QUERY_REQUESTS, "collectionId", collectionId).increment();
        log.info("query retrieval started traceId={} collectionId={} stage=query_processing", traceId, collectionId);
    }

    public void onFallback(String collectionId, String failedPath) {
        meterRegistry.counter(MetricNames.QUERY_FALLBACK, "collectionId", collectionId, "failedPath", failedPath).increment();
        log.warn("query fallback collectionId={} failedPath={} stage=retrieval_fallback", collectionId, failedPath);
    }

    public void onFailure(String collectionId) {
        meterRegistry.counter(MetricNames.QUERY_FAILED, "collectionId", collectionId).increment();
    }

    public void onEmptyResponse(String traceId) {
        log.info("query completed with empty result traceId={} stage=response_build", traceId);
    }

    public void onResponseBuilt(String traceId, int citations, Map<String, Object> debug) {
        meterRegistry.counter(
                MetricNames.QUERY_RESPONSE,
                "partialFallback",
                String.valueOf(debug.getOrDefault("partialFallback", false)),
                "rerankApplied",
                String.valueOf(debug.getOrDefault("rerankApplied", false))
        ).increment();
        log.info("query completed traceId={} stage=response_build citations={} debugKeys={}", traceId, citations, debug.keySet());
    }
}
