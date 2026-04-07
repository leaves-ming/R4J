package com.ming.rag.application.query;

import java.util.Map;

public record QueryCommand(
        String query,
        String collectionId,
        Map<String, Object> options,
        Integer denseTopK,
        Integer sparseTopK,
        Integer fusionTopK,
        Integer rerankTopK,
        boolean debug
) {
}
