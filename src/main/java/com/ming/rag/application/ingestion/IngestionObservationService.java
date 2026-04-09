package com.ming.rag.application.ingestion;

import com.ming.rag.observability.MetricNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IngestionObservationService {

    private static final Logger log = LoggerFactory.getLogger(IngestionObservationService.class);

    private final MeterRegistry meterRegistry;

    public IngestionObservationService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void onStarted(String traceId, String collectionId, String documentId) {
        meterRegistry.counter(MetricNames.INGESTION_TOTAL, "collectionId", collectionId).increment();
        log.info("ingestion started traceId={} collectionId={} documentId={} stage=received", traceId, collectionId, documentId);
    }

    public void onSkipped(String traceId, String collectionId, String documentId) {
        meterRegistry.counter(MetricNames.INGESTION_SKIPPED_TOTAL, "collectionId", collectionId).increment();
        log.info("ingestion skipped traceId={} collectionId={} documentId={} stage=dedupe", traceId, collectionId, documentId);
    }

    public void onProcessing(String traceId, String collectionId, String documentId) {
        log.info("ingestion processing traceId={} collectionId={} documentId={} stage=load", traceId, collectionId, documentId);
    }

    public void onReady(String traceId, String collectionId, String documentId, int chunkCount) {
        meterRegistry.counter(MetricNames.INGESTION_READY_TOTAL, "collectionId", collectionId).increment();
        meterRegistry.summary(MetricNames.INGESTION_CHUNK_COUNT, "collectionId", collectionId).record(chunkCount);
        log.info(
                "ingestion completed traceId={} collectionId={} documentId={} stage=ready chunkCount={}",
                traceId,
                collectionId,
                documentId,
                chunkCount
        );
    }

    public void onCompensation(String traceId, String collectionId, String documentId, String reason) {
        meterRegistry.counter(MetricNames.INGESTION_COMPENSATION_TOTAL, "collectionId", collectionId).increment();
        log.warn(
                "ingestion compensation traceId={} collectionId={} documentId={} stage=compensation reason={}",
                traceId,
                collectionId,
                documentId,
                reason
        );
    }

    public void onFailed(String traceId, String collectionId, String documentId, String reason) {
        meterRegistry.counter(MetricNames.INGESTION_FAILURE_TOTAL, "collectionId", collectionId).increment();
        log.error(
                "ingestion failed traceId={} collectionId={} documentId={} stage=failed reason={}",
                traceId,
                collectionId,
                documentId,
                reason
        );
    }

    public Map<String, Object> failureDetails(String collectionId, String documentId, String reason) {
        return Map.of("documentId", documentId, "collectionId", collectionId, "reason", reason);
    }
}
