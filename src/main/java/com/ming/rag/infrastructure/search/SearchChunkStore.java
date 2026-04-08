package com.ming.rag.infrastructure.search;

import com.ming.rag.domain.ingestion.Chunk;
import com.ming.rag.domain.ingestion.port.ChunkStorePort;
import com.ming.rag.domain.ingestion.port.EmbeddingPort;
import com.ming.rag.domain.ingestion.port.LexicalEncodingPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SearchChunkStore implements ChunkStorePort {

    private static final Logger log = LoggerFactory.getLogger(SearchChunkStore.class);

    private final SearchBackendClient searchBackendClient;
    private final EmbeddingPort embeddingPort;
    private final LexicalEncodingPort lexicalEncodingPort;
    private final Map<String, List<Chunk>> chunksByDocument = new ConcurrentHashMap<>();

    public SearchChunkStore(
            SearchBackendClient searchBackendClient,
            EmbeddingPort embeddingPort,
            LexicalEncodingPort lexicalEncodingPort
    ) {
        this.searchBackendClient = searchBackendClient;
        this.embeddingPort = embeddingPort;
        this.lexicalEncodingPort = lexicalEncodingPort;
    }

    @Override
    public void deleteByDocumentId(String collectionId, String documentId) {
        searchBackendClient.deleteByDocumentId(collectionId, documentId);
        chunksByDocument.remove(key(collectionId, documentId));
    }

    @Override
    public void upsert(String collectionId, List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        var texts = chunks.stream().map(Chunk::content).toList();
        var denseVectors = embeddingPort.embed(texts);
        var sparseTerms = lexicalEncodingPort.encode(texts);
        var records = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < chunks.size(); i++) {
            records.add(toSearchRecord(chunks.get(i), denseVectors.get(i), sparseTerms.get(i)));
        }
        var persisted = searchBackendClient.upsertRecords(records);
        if (!persisted) {
            log.info("Search backend unavailable, falling back to in-memory chunk store for collectionId={} documentId={}", collectionId, chunks.getFirst().documentId());
        }
        chunksByDocument.put(key(collectionId, chunks.getFirst().documentId()), List.copyOf(chunks));
    }

    @Override
    public int countVisibleChunks(String collectionId, String documentId) {
        var remoteCount = searchBackendClient.countVisibleChunks(collectionId, documentId);
        if (remoteCount >= 0) {
            return (int) remoteCount;
        }
        return chunksByDocument.getOrDefault(key(collectionId, documentId), List.of()).size();
    }

    public List<Chunk> findByDocumentId(String collectionId, String documentId) {
        return new ArrayList<>(chunksByDocument.getOrDefault(key(collectionId, documentId), List.of()));
    }

    public List<Chunk> findByCollection(String collectionId) {
        return findRecordsByCollection(collectionId).stream().map(SearchChunkRecord::chunk).toList();
    }

    public List<SearchChunkRecord> findRecordsByCollection(String collectionId) {
        var remote = searchBackendClient.findByCollection(collectionId);
        if (!remote.isEmpty()) {
            return remote.stream().map(this::toSearchChunkRecord).toList();
        }
        return chunksByDocument.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(collectionId + "::"))
                .flatMap(entry -> entry.getValue().stream())
                .map(this::toInMemoryRecord)
                .toList();
    }

    private Map<String, Object> toSearchRecord(Chunk chunk, float[] denseVector, Map<String, Integer> sparseTerms) {
        var metadata = new LinkedHashMap<>(chunk.metadata());
        metadata.put("document_id", chunk.documentId());
        metadata.put("chunk_index", chunk.chunkIndex());

        var record = new LinkedHashMap<String, Object>();
        record.put("id", chunk.collectionId() + ":" + chunk.chunkId());
        record.put("collection_id", chunk.collectionId());
        record.put("chunk_id", chunk.chunkId());
        record.put("document_id", chunk.documentId());
        record.put("chunk_index", chunk.chunkIndex());
        record.put("content", chunk.content());
        record.put("metadata_json", metadata);
        record.put("dense_vector", toFloatList(denseVector));
        record.put("sparse_terms", sparseTerms);
        record.put("ready", true);
        record.put("updated_at", searchBackendClient.nowIso());
        return record;
    }

    private List<Float> toFloatList(float[] denseVector) {
        var list = new ArrayList<Float>(denseVector.length);
        for (float value : denseVector) {
            list.add(value);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private SearchChunkRecord toSearchChunkRecord(Map<String, Object> record) {
        var metadata = (Map<String, Object>) record.getOrDefault("metadata_json", Map.of());
        var chunk = new Chunk(
                String.valueOf(record.get("chunk_id")),
                String.valueOf(record.get("document_id")),
                String.valueOf(record.get("collection_id")),
                ((Number) record.getOrDefault("chunk_index", 0)).intValue(),
                String.valueOf(record.get("content")),
                Map.copyOf(metadata)
        );
        var vector = ((List<?>) record.getOrDefault("dense_vector", List.of())).stream()
                .map(value -> ((Number) value).floatValue())
                .toList();
        var sparseTerms = new LinkedHashMap<String, Integer>();
        ((Map<?, ?>) record.getOrDefault("sparse_terms", Map.of())).forEach((key, value) -> sparseTerms.put(String.valueOf(key), ((Number) value).intValue()));
        return new SearchChunkRecord(chunk, toFloatArray(vector), Map.copyOf(sparseTerms));
    }

    private SearchChunkRecord toInMemoryRecord(Chunk chunk) {
        var vector = embeddingPort.embed(List.of(chunk.content())).getFirst();
        var sparseTerms = lexicalEncodingPort.encode(List.of(chunk.content())).getFirst();
        return new SearchChunkRecord(chunk, vector, sparseTerms);
    }

    private float[] toFloatArray(List<Float> vector) {
        var result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i);
        }
        return result;
    }

    private String key(String collectionId, String documentId) {
        return collectionId + "::" + documentId;
    }

    public record SearchChunkRecord(
            Chunk chunk,
            float[] denseVector,
            Map<String, Integer> sparseTerms
    ) {
    }
}
