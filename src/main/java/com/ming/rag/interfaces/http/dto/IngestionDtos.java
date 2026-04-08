package com.ming.rag.interfaces.http.dto;

public final class IngestionDtos {

    private IngestionDtos() {
    }

    public record IngestionResponse(
            String jobId,
            String documentId,
            String status,
            boolean skipped,
            int chunkCount,
            StorageInfo storage,
            String traceId
    ) {
    }

    public record StorageInfo(
            String metadata,
            String search
    ) {
    }
}
