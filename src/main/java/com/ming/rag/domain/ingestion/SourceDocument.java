package com.ming.rag.domain.ingestion;

public record SourceDocument(
        String documentId,
        String collectionId,
        String sourcePath,
        String originalFileName,
        String mediaType
) {
}
