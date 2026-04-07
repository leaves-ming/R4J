package com.ming.rag.infrastructure.search;

import com.ming.rag.domain.ingestion.Chunk;
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

    public DenseSearchAdapter(SearchChunkStore searchChunkStore) {
        this.searchChunkStore = searchChunkStore;
    }

    @Override
    public List<RetrievalCandidate> search(String collectionId, ProcessedQuery query, int topK) {
        if (query.normalizedQuery().toLowerCase(java.util.Locale.ROOT).contains("faildense")) {
            throw new IllegalStateException("Simulated dense failure");
        }
        return searchChunkStore.findByCollection(collectionId).stream()
                .filter(chunk -> matchesFilters(chunk, query.filters()))
                .map(chunk -> toCandidate(chunk, score(chunk, query.normalizedQuery()), "dense"))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed().thenComparing(RetrievalCandidate::chunkId))
                .limit(topK)
                .toList();
    }

    private double score(Chunk chunk, String normalizedQuery) {
        var content = chunk.content().toLowerCase(java.util.Locale.ROOT);
        var queryText = normalizedQuery.toLowerCase(java.util.Locale.ROOT);
        if (content.contains(queryText)) {
            return 10 + queryText.length();
        }
        double partial = 0.0d;
        for (String token : queryText.split("[^\\p{Alnum}_]+")) {
            if (!token.isBlank() && content.contains(token)) {
                partial += 1.0d;
            }
        }
        return partial;
    }

    private boolean matchesFilters(Chunk chunk, Map<String, Object> filters) {
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

    private RetrievalCandidate toCandidate(Chunk chunk, double score, String matchedBy) {
        var metadata = new java.util.LinkedHashMap<String, Object>(chunk.metadata());
        metadata.put("document_id", chunk.documentId());
        metadata.putIfAbsent("source_path", chunk.metadata().get("source_path"));
        return new RetrievalCandidate(chunk.chunkId(), score, matchedBy, chunk.content(), Map.copyOf(metadata));
    }
}
