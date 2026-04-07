package com.ming.rag.domain.ingestion.port;

import com.ming.rag.domain.ingestion.Chunk;
import java.util.List;

public interface ChunkStorePort {

    void deleteByDocumentId(String collectionId, String documentId);

    void upsert(String collectionId, List<Chunk> chunks);

    int countVisibleChunks(String collectionId, String documentId);
}
