package com.ming.rag.infrastructure.search;

import com.ming.rag.domain.ingestion.Chunk;
import com.ming.rag.domain.ingestion.port.ChunkStorePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SearchChunkStore implements ChunkStorePort {

    private final Map<String, List<Chunk>> chunksByDocument = new ConcurrentHashMap<>();

    @Override
    public void deleteByDocumentId(String collectionId, String documentId) {
        chunksByDocument.remove(key(collectionId, documentId));
    }

    @Override
    public void upsert(String collectionId, List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        chunksByDocument.put(key(collectionId, chunks.getFirst().documentId()), List.copyOf(chunks));
    }

    @Override
    public int countVisibleChunks(String collectionId, String documentId) {
        return chunksByDocument.getOrDefault(key(collectionId, documentId), List.of()).size();
    }

    public List<Chunk> findByDocumentId(String collectionId, String documentId) {
        return new ArrayList<>(chunksByDocument.getOrDefault(key(collectionId, documentId), List.of()));
    }

    public List<Chunk> findByCollection(String collectionId) {
        return chunksByDocument.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(collectionId + "::"))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
    }

    private String key(String collectionId, String documentId) {
        return collectionId + "::" + documentId;
    }
}
