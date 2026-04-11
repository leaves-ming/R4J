package com.ming.rag.application.query;

import com.ming.rag.observability.MetricNames;
import com.ming.rag.domain.query.ToolExecutionResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
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
        meterRegistry.counter(MetricNames.QUERY_TOTAL, "collectionId", collectionId).increment();
        log.info("query retrieval started traceId={} collectionId={} stage=query_processing", traceId, collectionId);
    }

    public void onQueryProcessed(String traceId, String collectionId, long elapsedMs, int keywordCount) {
        meterRegistry.timer(MetricNames.QUERY_LATENCY_MS, "collectionId", collectionId, "stage", "query_processing")
                .record(elapsedMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info(
                "query stage traceId={} collectionId={} stage=query_processing elapsedMs={} keywordCount={}",
                traceId,
                collectionId,
                elapsedMs,
                keywordCount
        );
    }

    public void onDenseCompleted(String traceId, String collectionId, long elapsedMs, boolean failed) {
        meterRegistry.timer(MetricNames.DENSE_LATENCY_MS, "collectionId", collectionId, "failed", String.valueOf(failed))
                .record(elapsedMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info(
                "query stage traceId={} collectionId={} stage=dense_search elapsedMs={} failed={}",
                traceId,
                collectionId,
                elapsedMs,
                failed
        );
    }

    public void onSparseCompleted(String traceId, String collectionId, long elapsedMs, boolean failed) {
        meterRegistry.timer(MetricNames.SPARSE_LATENCY_MS, "collectionId", collectionId, "failed", String.valueOf(failed))
                .record(elapsedMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info(
                "query stage traceId={} collectionId={} stage=sparse_search elapsedMs={} failed={}",
                traceId,
                collectionId,
                elapsedMs,
                failed
        );
    }

    public void onFusionCompleted(String traceId, String collectionId, long elapsedMs, boolean partialFallback, int resultCount) {
        meterRegistry.timer(MetricNames.QUERY_LATENCY_MS, "collectionId", collectionId, "stage", "fusion")
                .record(elapsedMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info(
                "query stage traceId={} collectionId={} stage=fusion elapsedMs={} partialFallback={} resultCount={}",
                traceId,
                collectionId,
                elapsedMs,
                partialFallback,
                resultCount
        );
    }

    public void onRerankCompleted(String traceId, String collectionId, long elapsedMs, String provider, boolean applied) {
        meterRegistry.timer(
                        MetricNames.RERANK_LATENCY_MS,
                        "collectionId",
                        collectionId,
                        "provider",
                        provider,
                        "applied",
                        String.valueOf(applied)
                )
                .record(elapsedMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info(
                "query stage traceId={} collectionId={} stage=rerank elapsedMs={} provider={} applied={}",
                traceId,
                collectionId,
                elapsedMs,
                provider,
                applied
        );
    }

    public void onFallback(String collectionId, String failedPath) {
        meterRegistry.counter(MetricNames.RETRIEVAL_FALLBACK_TOTAL, "collectionId", collectionId, "failedPath", failedPath).increment();
        log.warn("query fallback collectionId={} failedPath={} stage=retrieval_fallback", collectionId, failedPath);
    }

    public void onFailure(String collectionId) {
        meterRegistry.counter(MetricNames.QUERY_FAILURE_TOTAL, "collectionId", collectionId).increment();
    }

    public void onAdvisorDecision(String route, int candidateToolCount) {
        meterRegistry.counter(MetricNames.MCP_ADVISOR_DECISION_TOTAL, "route", route).increment();
        log.info("query advisor decision route={} candidateToolCount={} stage=advisor_route", route, candidateToolCount);
    }

    public void onMcpInvocation(List<ToolExecutionResult> results) {
        for (var result : results) {
            meterRegistry.timer(
                            MetricNames.MCP_LATENCY_MS,
                            "serverId",
                            result.serverId(),
                            "toolName",
                            result.toolName(),
                            "success",
                            String.valueOf(result.success())
                    )
                    .record(result.latencyMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!result.success()) {
                meterRegistry.counter(
                        MetricNames.MCP_FAILURE_TOTAL,
                        "serverId",
                        result.serverId(),
                        "toolName",
                        result.toolName(),
                        "failureType",
                        String.valueOf(result.failureType())
                ).increment();
            }
            log.info(
                    "query stage stage=mcp_invoke serverId={} toolName={} latencyMs={} success={} failureType={}",
                    result.serverId(),
                    result.toolName(),
                    result.latencyMs(),
                    result.success(),
                    result.failureType()
            );
        }
    }

    public void onEmptyResponse(String traceId) {
        log.info("query completed with empty result traceId={} stage=answer_assemble", traceId);
    }

    public void onResponseBuilt(String traceId, int citations, Map<String, Object> debug) {
        meterRegistry.counter(
                MetricNames.QUERY_RESPONSE_TOTAL,
                "partialFallback",
                String.valueOf(debug.getOrDefault("partialFallback", false)),
                "rerankApplied",
                String.valueOf(debug.getOrDefault("rerankApplied", false)),
                "mcpFallback",
                String.valueOf(debug.getOrDefault("mcpFallback", false))
        ).increment();
        log.info("query completed traceId={} stage=answer_assemble citations={} debugKeys={}", traceId, citations, debug.keySet());
    }
}
