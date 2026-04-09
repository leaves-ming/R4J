package com.ming.rag.application.evaluation;

import com.ming.rag.observability.MetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EvaluationObservationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationObservationService.class);

    private final MeterRegistry meterRegistry;

    public EvaluationObservationService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void onStarted(String traceId, String collectionId) {
        meterRegistry.counter(MetricNames.EVALUATION_RUN_TOTAL, "collectionId", collectionId).increment();
        log.info("evaluation started traceId={} collectionId={} stage=evaluation_load_test_set", traceId, collectionId);
    }

    public void onCasesLoaded(String traceId, String collectionId, int totalCases) {
        log.info(
                "evaluation stage traceId={} collectionId={} stage=evaluation_load_cases totalCases={}",
                traceId,
                collectionId,
                totalCases
        );
    }

    public void onRetrieveCase(String traceId, String collectionId, String query) {
        log.info("evaluation stage traceId={} collectionId={} stage=evaluation_retrieve query={}", traceId, collectionId, query);
    }

    public void onAnswerCase(String traceId, String collectionId, String query) {
        log.info("evaluation stage traceId={} collectionId={} stage=evaluation_answer query={}", traceId, collectionId, query);
    }

    public void onScoreCase(String traceId, String collectionId, String query) {
        log.info("evaluation stage traceId={} collectionId={} stage=evaluation_score query={}", traceId, collectionId, query);
    }

    public void onPersist(String traceId, String collectionId, String runId) {
        log.info("evaluation stage traceId={} collectionId={} stage=evaluation_persist runId={}", traceId, collectionId, runId);
    }

    public void onCompleted(String traceId, String collectionId, int totalCases) {
        meterRegistry.counter(MetricNames.EVALUATION_CASE_TOTAL, "collectionId", collectionId).increment(totalCases);
        log.info("evaluation completed traceId={} collectionId={} stage=evaluation_aggregate totalCases={}", traceId, collectionId, totalCases);
    }
}
