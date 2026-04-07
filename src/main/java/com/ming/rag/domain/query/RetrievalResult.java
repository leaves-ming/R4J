package com.ming.rag.domain.query;

import java.util.List;
import java.util.Map;

public record RetrievalResult(
        ProcessedQuery processedQuery,
        List<RankedResult> topKResults,
        boolean partialFallback,
        String traceId,
        Map<String, Object> debug
) {
}
