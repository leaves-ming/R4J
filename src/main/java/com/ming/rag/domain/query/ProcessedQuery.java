package com.ming.rag.domain.query;

import java.util.List;
import java.util.Map;

public record ProcessedQuery(
        String rawQuery,
        String normalizedQuery,
        List<String> keywords,
        Map<String, Object> filters
) {
}
