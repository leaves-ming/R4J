package com.ming.rag.infrastructure.search;

import com.ming.rag.domain.ingestion.port.EmbeddingPort;
import com.ming.rag.domain.query.ProcessedQuery;
import com.ming.rag.domain.query.RetrievalCandidate;
import com.ming.rag.domain.query.port.DenseSearchPort;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DenseSearchAdapter implements DenseSearchPort {

    private final SearchChunkStore searchChunkStore;
    private final EmbeddingPort embeddingPort;

    public DenseSearchAdapter(SearchChunkStore searchChunkStore, EmbeddingPort embeddingPort) {
        this.searchChunkStore = searchChunkStore;
        this.embeddingPort = embeddingPort;
    }

    @Override
    public List<RetrievalCandidate> search(String collectionId, ProcessedQuery query, int topK) {
        if (query.normalizedQuery().toLowerCase(java.util.Locale.ROOT).contains("faildense")) {
            throw new IllegalStateException("Simulated dense failure");
        }
        var queryVector = embeddingPort.embed(List.of(query.normalizedQuery())).getFirst();
        return searchChunkStore.findRecordsByCollection(collectionId).stream()
                .filter(record -> matchesFilters(record.chunk(), query.filters()))
                .map(record -> toCandidate(record.chunk(), score(record.chunk().content(), record.denseVector(), queryVector, query.keywords()), "dense"))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed().thenComparing(RetrievalCandidate::chunkId))
                .limit(topK)
                .toList();
    }

    private double score(String content, float[] documentVector, float[] queryVector, List<String> keywords) {
        var lower = content.toLowerCase(java.util.Locale.ROOT);
        var overlap = keywords.stream().filter(lower::contains).count();
        if (overlap == 0) {
            return 0.0d;
        }
        if (documentVector == null || queryVector == null || documentVector.length == 0 || queryVector.length == 0) {
            return 0.0d;
        }
        var length = Math.min(documentVector.length, queryVector.length);
        double dot = 0.0d;
        double left = 0.0d;
        double right = 0.0d;
        for (int i = 0; i < length; i++) {
            dot += documentVector[i] * queryVector[i];
            left += documentVector[i] * documentVector[i];
            right += queryVector[i] * queryVector[i];
        }
        if (left == 0.0d || right == 0.0d) {
            return 0.0d;
        }
        return (dot / (Math.sqrt(left) * Math.sqrt(right))) + overlap;
    }

    private boolean matchesFilters(com.ming.rag.domain.ingestion.Chunk chunk, Map<String, Object> filters) {
        for (var entry : filters.entrySet()) {
            if ("collection".equals(entry.getKey())) {
                continue;
            }
            var actual = chunk.metadata().get(entry.getKey());
            if ("tags".equals(entry.getKey()) && actual instanceof List<?> actualTags && entry.getValue() instanceof List<?> expectedTags) {
                if (!actualTags.containsAll(expectedTags)) {
                    return false;
                }
                continue;
            }
            if (actual == null || !String.valueOf(actual).equals(String.valueOf(entry.getValue()))) {
                return false;
            }
        }
        return true;
    }

    private RetrievalCandidate toCandidate(com.ming.rag.domain.ingestion.Chunk chunk, double score, String matchedBy) {
        var metadata = new java.util.LinkedHashMap<String, Object>(chunk.metadata());
        metadata.put("document_id", chunk.documentId());
        metadata.putIfAbsent("source_path", chunk.metadata().get("source_path"));
        return new RetrievalCandidate(chunk.chunkId(), score, matchedBy, chunk.content(), Map.copyOf(metadata));
    }
}
