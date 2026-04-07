package com.ming.rag.domain.query;

import java.util.Map;

public record RetrievalCandidate(
        String chunkId,
        double score,
        String matchedBy,
        String content,
        Map<String, Object> metadata
) {
}
