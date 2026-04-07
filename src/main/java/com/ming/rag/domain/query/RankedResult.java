package com.ming.rag.domain.query;

import java.util.Map;

public record RankedResult(
        String chunkId,
        double score,
        int rank,
        String content,
        Map<String, Object> metadata
) {
}
