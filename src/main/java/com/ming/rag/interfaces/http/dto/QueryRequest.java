package com.ming.rag.interfaces.http.dto;

import java.util.Map;

public record QueryRequest(
        String query,
        String collectionId,
        Map<String, Object> options,
        Integer denseTopK,
        Integer sparseTopK,
        Integer fusionTopK,
        Integer rerankTopK,
        Boolean debug
) {
}
