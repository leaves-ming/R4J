package com.ming.rag.domain.ingestion.port;

public interface DocumentRegistryPort {

    boolean shouldSkip(String collectionId, String documentId);

    void markProcessing(String collectionId, String documentId, String sourcePath, String originalFileName, String mediaType);

    void markReady(String collectionId, String documentId, int chunkCount);

    void markFailed(String collectionId, String documentId, String errorReason);

    boolean isReady(String collectionId, String documentId);
}
