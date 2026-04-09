package com.ming.rag.infrastructure.search;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.ingestion.Chunk;
import com.ming.rag.domain.ingestion.ChunkRecord;
import com.ming.rag.domain.ingestion.port.ChunkStorePort;
import com.ming.rag.domain.ingestion.port.EmbeddingPort;
import com.ming.rag.domain.ingestion.port.LexicalEncodingPort;
import java.time.Instant;
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
    private final RagProperties ragProperties;
    private final EmbeddingPort embeddingPort;
    private final LexicalEncodingPort lexicalEncodingPort;
    private final Map<String, List<ChunkRecord>> chunkRecordsByDocument = new ConcurrentHashMap<>();

    public SearchChunkStore(
            SearchBackendClient searchBackendClient,
            RagProperties ragProperties,
            EmbeddingPort embeddingPort,
            LexicalEncodingPort lexicalEncodingPort
    ) {
        this.searchBackendClient = searchBackendClient;
        this.ragProperties = ragProperties;
        this.embeddingPort = embeddingPort;
        this.lexicalEncodingPort = lexicalEncodingPort;
    }

    @Override
    public void deleteByDocumentId(String collectionId, String documentId) {
        try {
            searchBackendClient.deleteByDocumentId(collectionId, documentId);
        } catch (RuntimeException exception) {
            if (!ragProperties.storage().search().devFallbackEnabled()) {
                throw exception;
            }
            log.info("search delete falling back to in-memory store collectionId={} documentId={}", collectionId, documentId);
        }
        chunkRecordsByDocument.remove(key(collectionId, documentId));
    }

    @Override
    public void upsert(String collectionId, List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        var texts = chunks.stream().map(Chunk::content).toList();
        var denseVectors = embeddingPort.embed(texts);
        var sparseTerms = lexicalEncodingPort.encode(texts);
        var now = Instant.now();
        var records = new ArrayList<ChunkRecord>();
        for (int i = 0; i < chunks.size(); i++) {
            records.add(toChunkRecord(chunks.get(i), denseVectors.get(i), sparseTerms.get(i), now));
        }
        try {
            searchBackendClient.upsertRecords(records);
        } catch (RuntimeException exception) {
            if (!ragProperties.storage().search().devFallbackEnabled()) {
                throw exception;
            }
            log.info("Search backend unavailable, falling back to in-memory chunk store for collectionId={} documentId={}", collectionId, chunks.getFirst().documentId());
        }
        chunkRecordsByDocument.put(key(collectionId, chunks.getFirst().documentId()), List.copyOf(records));
    }

    @Override
    public int countVisibleChunks(String collectionId, String documentId) {
        try {
            return (int) searchBackendClient.countVisibleChunks(collectionId, documentId);
        } catch (RuntimeException exception) {
            if (!ragProperties.storage().search().devFallbackEnabled()) {
                throw exception;
            }
        }
        return chunkRecordsByDocument.getOrDefault(key(collectionId, documentId), List.of()).size();
    }

    public List<Chunk> findByDocumentId(String collectionId, String documentId) {
        return chunkRecordsByDocument.getOrDefault(key(collectionId, documentId), List.of()).stream()
                .map(this::toChunk)
                .toList();
    }

    public List<Chunk> findByCollection(String collectionId) {
        return findRecordsByCollection(collectionId).stream().map(this::toChunk).toList();
    }

    public List<ChunkRecord> findRecordsByCollection(String collectionId) {
        try {
            return searchBackendClient.findByCollection(collectionId);
        } catch (RuntimeException exception) {
            if (!ragProperties.storage().search().devFallbackEnabled()) {
                throw exception;
            }
        }
        return chunkRecordsByDocument.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(collectionId + "::"))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
    }

    private ChunkRecord toChunkRecord(Chunk chunk, float[] denseVector, Map<String, Integer> sparseTerms, Instant now) {
        var metadata = new LinkedHashMap<>(chunk.metadata());
        metadata.put("document_id", chunk.documentId());
        metadata.put("chunk_index", chunk.chunkIndex());
        return new ChunkRecord(
                chunk.chunkId(),
                chunk.documentId(),
                chunk.collectionId(),
                chunk.chunkIndex(),
                chunk.content(),
                Map.copyOf(metadata),
                toFloatList(denseVector),
                Map.copyOf(sparseTerms),
                true,
                now,
                now
        );
    }

    private List<Float> toFloatList(float[] denseVector) {
        var list = new ArrayList<Float>(denseVector.length);
        for (float value : denseVector) {
            list.add(value);
        }
        return list;
    }

    private Chunk toChunk(ChunkRecord record) {
        return new Chunk(
                record.chunkId(),
                record.documentId(),
                record.collectionId(),
                record.chunkIndex(),
                record.content(),
                record.metadata()
        );
    }

    private float[] toFloatArray(List<Float> vector) {
        var result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i);
        }
        return result;
    }

    public float[] denseVectorOf(ChunkRecord record) {
        return toFloatArray(record.denseVector());
    }

    public Map<String, Integer> sparseTermsOf(ChunkRecord record) {
        return record.sparseTerms();
    }

    private ChunkRecord toInMemoryRecord(Chunk chunk) {
        var vector = embeddingPort.embed(List.of(chunk.content())).getFirst();
        var sparseTerms = lexicalEncodingPort.encode(List.of(chunk.content())).getFirst();
        return toChunkRecord(chunk, vector, sparseTerms, Instant.now());
    }

    public record SearchChunkRecord(
            Chunk chunk,
            float[] denseVector,
            Map<String, Integer> sparseTerms
    ) {
    }

    public SearchChunkRecord toSearchChunkRecord(ChunkRecord record) {
        var chunk = new Chunk(
                record.chunkId(),
                record.documentId(),
                record.collectionId(),
                record.chunkIndex(),
                record.content(),
                record.metadata()
        );
        return new SearchChunkRecord(chunk, denseVectorOf(record), sparseTermsOf(record));
    }

    private String key(String collectionId, String documentId) {
        return collectionId + "::" + documentId;
    }
}
