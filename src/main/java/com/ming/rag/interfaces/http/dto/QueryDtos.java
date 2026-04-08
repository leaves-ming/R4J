package com.ming.rag.interfaces.http.dto;

import com.ming.rag.domain.response.Citation;
import java.util.List;
import java.util.Map;

public final class QueryDtos {

    private QueryDtos() {
    }

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

    public record QueryResponse(
            boolean empty,
            String answer,
            List<Citation> citations,
            String traceId,
            Map<String, Object> debug
    ) {
    }
}
