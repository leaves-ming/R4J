package com.ming.rag.domain.response;

import java.util.Map;

public record Citation(
        int index,
        String chunkId,
        String documentId,
        String sourcePath,
        Integer page,
        double score,
        String snippet,
        Map<String, Object> metadata
) {
}
