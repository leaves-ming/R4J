package com.ming.rag.domain.ingestion.port;

import com.ming.rag.domain.ingestion.ChunkRecord;
import java.util.List;

public interface ChunkStorePort {

    void deleteByDocumentId(String collectionId, String documentId);

    void upsert(String collectionId, List<ChunkRecord> records);

    int countVisibleChunks(String collectionId, String documentId);
}
