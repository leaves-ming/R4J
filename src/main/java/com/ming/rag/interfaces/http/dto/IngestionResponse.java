package com.ming.rag.interfaces.http.dto;

public record IngestionResponse(
        String jobId,
        String documentId,
        String status,
        boolean skipped,
        int chunkCount,
        String traceId
) {
}
