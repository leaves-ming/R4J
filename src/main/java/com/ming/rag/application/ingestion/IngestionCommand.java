package com.ming.rag.application.ingestion;

public record IngestionCommand(
        String collectionId,
        String originalFileName,
        String mediaType,
        byte[] fileBytes,
        boolean forceReingest,
        Integer chunkSizeOverride,
        Integer chunkOverlapOverride
) {
}
