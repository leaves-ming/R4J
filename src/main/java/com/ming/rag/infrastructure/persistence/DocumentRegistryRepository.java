package com.ming.rag.infrastructure.persistence;

import com.ming.rag.domain.ingestion.DocumentStatus;
import com.ming.rag.domain.ingestion.port.DocumentRegistryPort;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class DocumentRegistryRepository implements DocumentRegistryPort {

    private final Map<String, RegistryEntry> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean failNextMarkReady = new AtomicBoolean(false);

    @Override
    public boolean shouldSkip(String collectionId, String documentId) {
        return statusOf(collectionId, documentId) == DocumentStatus.READY;
    }

    @Override
    public void markProcessing(String collectionId, String documentId, String sourcePath, String originalFileName, String mediaType) {
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
        var current = requiredEntry(collectionId, documentId);
        entries.put(key(collectionId, documentId), current.withStatus(DocumentStatus.READY, chunkCount, null, Instant.now()));
    }

    @Override
    public void markFailed(String collectionId, String documentId, String errorReason) {
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
