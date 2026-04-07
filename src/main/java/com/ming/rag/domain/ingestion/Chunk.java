package com.ming.rag.domain.ingestion;

import java.util.Map;

public record Chunk(
        String chunkId,
        String documentId,
        String collectionId,
        int chunkIndex,
        String content,
        Map<String, Object> metadata
) {
}
