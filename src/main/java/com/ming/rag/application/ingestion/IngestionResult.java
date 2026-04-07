package com.ming.rag.application.ingestion;

public record IngestionResult(
        String jobId,
        String documentId,
        String status,
        boolean skipped,
        int chunkCount,
        String traceId
) {
}
