package com.ming.rag.infrastructure.persistence;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.ingestion.DocumentStatus;
import com.ming.rag.domain.ingestion.port.DocumentRegistryPort;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DocumentRegistryRepository implements DocumentRegistryPort {

    private static final String UPSERT_PROCESSING = """
            INSERT INTO document_registry (
                collection_id, document_id, status, source_path, original_file_name, media_type, chunk_count, error_reason, created_at, updated_at, ready_at
            ) VALUES (?, ?, ?, ?, ?, ?, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
            ON CONFLICT (collection_id, document_id) DO UPDATE SET
                status = EXCLUDED.status,
                source_path = EXCLUDED.source_path,
                original_file_name = EXCLUDED.original_file_name,
                media_type = EXCLUDED.media_type,
                updated_at = CURRENT_TIMESTAMP,
                error_reason = NULL,
                ready_at = NULL
            """;
    private static final String UPDATE_READY = """
            UPDATE document_registry
               SET status = ?, chunk_count = ?, error_reason = NULL, updated_at = CURRENT_TIMESTAMP, ready_at = CURRENT_TIMESTAMP
             WHERE collection_id = ? AND document_id = ?
            """;
    private static final String UPDATE_FAILED = """
            UPDATE document_registry
               SET status = ?, error_reason = ?, updated_at = CURRENT_TIMESTAMP, ready_at = NULL
             WHERE collection_id = ? AND document_id = ?
            """;
    private static final String SELECT_STATUS = """
            SELECT status
              FROM document_registry
             WHERE collection_id = ? AND document_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RagProperties ragProperties;
    private final Map<String, RegistryEntry> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean failNextMarkReady = new AtomicBoolean(false);

    public DocumentRegistryRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, RagProperties ragProperties) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.ragProperties = ragProperties;
    }

    @Override
    public boolean shouldSkip(String collectionId, String documentId) {
        return statusOf(collectionId, documentId) == DocumentStatus.READY;
    }

    @Override
    public void markProcessing(String collectionId, String documentId, String sourcePath, String originalFileName, String mediaType) {
        if (jdbcTemplate != null) {
            jdbcTemplate.update(UPSERT_PROCESSING, collectionId, documentId, DocumentStatus.PROCESSING.name(), sourcePath, originalFileName, mediaType);
            return;
        }
        requireFallbackAllowed("markProcessing");
        entries.put(key(collectionId, documentId), new RegistryEntry(
                collectionId,
                documentId,
                DocumentStatus.PROCESSING,
                sourcePath,
                originalFileName,
                mediaType,
                0,
                null,
                Instant.now(),
                Instant.now(),
                null
        ));
    }

    @Override
    public void markReady(String collectionId, String documentId, int chunkCount) {
        if (failNextMarkReady.compareAndSet(true, false)) {
            throw new IllegalStateException("Simulated markReady failure");
        }
        if (jdbcTemplate != null) {
            var updated = jdbcTemplate.update(UPDATE_READY, DocumentStatus.READY.name(), chunkCount, collectionId, documentId);
            if (updated == 0) {
                throw new IllegalStateException("Registry entry does not exist");
            }
            return;
        }
        requireFallbackAllowed("markReady");
        var current = requiredEntry(collectionId, documentId);
        entries.put(key(collectionId, documentId), current.withStatus(DocumentStatus.READY, chunkCount, null, Instant.now()));
    }

    @Override
    public void markFailed(String collectionId, String documentId, String errorReason) {
        if (jdbcTemplate != null) {
            jdbcTemplate.update(UPDATE_FAILED, DocumentStatus.FAILED.name(), errorReason, collectionId, documentId);
            return;
        }
        requireFallbackAllowed("markFailed");
        var current = entries.get(key(collectionId, documentId));
        if (current == null) {
            return;
        }
        entries.put(key(collectionId, documentId), current.withStatus(DocumentStatus.FAILED, current.chunkCount(), errorReason, null));
    }

    @Override
    public boolean isReady(String collectionId, String documentId) {
        return statusOf(collectionId, documentId) == DocumentStatus.READY;
    }

    public DocumentStatus statusOf(String collectionId, String documentId) {
        if (jdbcTemplate != null) {
            var statuses = jdbcTemplate.query(SELECT_STATUS, (rs, rowNum) -> DocumentStatus.valueOf(rs.getString("status")), collectionId, documentId);
            return statuses.isEmpty() ? DocumentStatus.RECEIVED : statuses.getFirst();
        }
        requireFallbackAllowed("statusOf");
        var entry = entries.get(key(collectionId, documentId));
        return entry == null ? DocumentStatus.RECEIVED : entry.status();
    }

    public void failNextMarkReady() {
        failNextMarkReady.set(true);
    }

    private RegistryEntry requiredEntry(String collectionId, String documentId) {
        var entry = entries.get(key(collectionId, documentId));
        if (entry == null) {
            throw new IllegalStateException("Registry entry does not exist");
        }
        return entry;
    }

    private String key(String collectionId, String documentId) {
        return collectionId + "::" + documentId;
    }

    private void requireFallbackAllowed(String operation) {
        if (ragProperties.storage().metadata().required()) {
            throw new IllegalStateException("JDBC metadata repository is required for operation: " + operation);
        }
    }

    private record RegistryEntry(
            String collectionId,
            String documentId,
            DocumentStatus status,
            String sourcePath,
            String originalFileName,
            String mediaType,
            int chunkCount,
            String errorReason,
            Instant createdAt,
            Instant updatedAt,
            Instant readyAt
    ) {
        private RegistryEntry withStatus(DocumentStatus newStatus, int newChunkCount, String newErrorReason, Instant newReadyAt) {
            return new RegistryEntry(
                    collectionId,
                    documentId,
                    newStatus,
                    sourcePath,
                    originalFileName,
                    mediaType,
                    newChunkCount,
                    newErrorReason,
                    createdAt,
                    Instant.now(),
                    newReadyAt
            );
        }
    }
}
