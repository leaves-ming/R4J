package com.ming.rag.infrastructure.persistence;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.common.JobId;
import com.ming.rag.domain.ingestion.DocumentStatus;
import java.time.Instant;
import java.sql.Types;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IngestionJobRepository {

    private static final String UPSERT_JOB = """
            INSERT INTO ingestion_job (
                job_id, collection_id, document_id, status, force_reingest, requested_chunk_size, requested_chunk_overlap, error_reason, trace_id, created_at, updated_at
            ) VALUES (CAST(? AS UUID), ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (job_id) DO UPDATE SET
                status = EXCLUDED.status,
                error_reason = EXCLUDED.error_reason,
                updated_at = CURRENT_TIMESTAMP
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RagProperties ragProperties;
    private final Map<String, IngestionJobEntry> entries = new ConcurrentHashMap<>();

    public IngestionJobRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, RagProperties ragProperties) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.ragProperties = ragProperties;
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
            jdbcTemplate.update(connection -> {
                var statement = connection.prepareStatement(UPSERT_JOB);
                statement.setObject(1, UUID.fromString(jobId.value()), Types.OTHER);
                statement.setString(2, collectionId);
                statement.setString(3, documentId);
                statement.setString(4, status.name());
                statement.setBoolean(5, forceReingest);
                statement.setInt(6, chunkSize == null ? 0 : chunkSize);
                statement.setInt(7, chunkOverlap == null ? 0 : chunkOverlap);
                statement.setString(8, errorReason);
                statement.setString(9, traceId);
                return statement;
            });
            return;
        }
        requireFallbackAllowed();

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

    private void requireFallbackAllowed() {
        if (ragProperties.storage().metadata().required()) {
            throw new IllegalStateException("JDBC ingestion job repository is required");
        }
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
