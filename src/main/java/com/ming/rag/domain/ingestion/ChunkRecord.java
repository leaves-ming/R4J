package com.ming.rag.domain.ingestion;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ChunkRecord(
        String chunkId,
        String documentId,
        String collectionId,
        int chunkIndex,
        String content,
        Map<String, Object> metadata,
        List<Float> denseVector,
        Map<String, Integer> sparseTerms,
        boolean ready,
        Instant createdAt,
        Instant updatedAt
) {
}
