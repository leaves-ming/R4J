package com.ming.rag.infrastructure.persistence;

import com.ming.rag.domain.common.JobId;
import com.ming.rag.domain.ingestion.DocumentStatus;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IngestionJobRepository {

    private static final String UPSERT_JOB = """
            INSERT INTO ingestion_job (
                job_id, collection_id, document_id, status, force_reingest, requested_chunk_size, requested_chunk_overlap, error_reason, trace_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (job_id) DO UPDATE SET
                status = EXCLUDED.status,
                error_reason = EXCLUDED.error_reason,
                updated_at = CURRENT_TIMESTAMP
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, IngestionJobEntry> entries = new ConcurrentHashMap<>();

    public IngestionJobRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    public void markProcessing(
            JobId jobId,
            String collectionId,
            String documentId,
            boolean forceReingest,
            Integer chunkSize,
            Integer chunkOverlap,
            String traceId
    ) {
        upsert(jobId, collectionId, documentId, DocumentStatus.PROCESSING, forceReingest, chunkSize, chunkOverlap, null, traceId);
    }

    public void markReady(
            JobId jobId,
            String collectionId,
            String documentId,
            boolean forceReingest,
            Integer chunkSize,
            Integer chunkOverlap,
            String traceId
    ) {
        upsert(jobId, collectionId, documentId, DocumentStatus.READY, forceReingest, chunkSize, chunkOverlap, null, traceId);
    }

    public void markFailed(
            JobId jobId,
            String collectionId,
            String documentId,
            boolean forceReingest,
            Integer chunkSize,
            Integer chunkOverlap,
            String traceId,
            String errorReason
    ) {
        upsert(jobId, collectionId, documentId, DocumentStatus.FAILED, forceReingest, chunkSize, chunkOverlap, errorReason, traceId);
    }

    public void markSkipped(
            JobId jobId,
            String collectionId,
            String documentId,
            boolean forceReingest,
            Integer chunkSize,
            Integer chunkOverlap,
            String traceId
    ) {
        upsert(jobId, collectionId, documentId, DocumentStatus.READY, forceReingest, chunkSize, chunkOverlap, "SKIPPED_DUPLICATE_READY", traceId);
    }

    public IngestionJobEntry findByJobId(String jobId) {
        return entries.get(jobId);
    }

    private void upsert(
            JobId jobId,
            String collectionId,
            String documentId,
            DocumentStatus status,
            boolean forceReingest,
            Integer chunkSize,
            Integer chunkOverlap,
            String errorReason,
            String traceId
    ) {
        if (jdbcTemplate != null) {
            jdbcTemplate.update(
                    UPSERT_JOB,
                    jobId.value(),
                    collectionId,
                    documentId,
                    status.name(),
                    forceReingest,
                    chunkSize == null ? 0 : chunkSize,
                    chunkOverlap == null ? 0 : chunkOverlap,
                    errorReason,
                    traceId
            );
            return;
        }

        entries.put(jobId.value(), new IngestionJobEntry(
                jobId.value(),
                collectionId,
                documentId,
                status,
                forceReingest,
                chunkSize == null ? 0 : chunkSize,
                chunkOverlap == null ? 0 : chunkOverlap,
                errorReason,
                traceId,
                Instant.now()
        ));
    }

    public record IngestionJobEntry(
            String jobId,
            String collectionId,
            String documentId,
            DocumentStatus status,
            boolean forceReingest,
            int requestedChunkSize,
            int requestedChunkOverlap,
            String errorReason,
            String traceId,
            Instant updatedAt
    ) {
    }
}
