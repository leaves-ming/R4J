package com.ming.rag.domain.ingestion;

import java.util.Map;

public record ParsedDocument(
        String documentId,
        String collectionId,
        String content,
        Map<String, Object> metadata
) {
}
