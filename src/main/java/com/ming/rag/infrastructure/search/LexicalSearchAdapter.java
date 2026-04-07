package com.ming.rag.infrastructure.search;

import com.ming.rag.domain.ingestion.Chunk;
import com.ming.rag.domain.query.ProcessedQuery;
import com.ming.rag.domain.query.RetrievalCandidate;
import com.ming.rag.domain.query.port.LexicalSearchPort;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LexicalSearchAdapter implements LexicalSearchPort {

    private final SearchChunkStore searchChunkStore;

    public LexicalSearchAdapter(SearchChunkStore searchChunkStore) {
        this.searchChunkStore = searchChunkStore;
    }

    @Override
    public List<RetrievalCandidate> search(String collectionId, ProcessedQuery query, int topK) {
        if (query.normalizedQuery().toLowerCase(java.util.Locale.ROOT).contains("failsparse")) {
            throw new IllegalStateException("Simulated sparse failure");
        }
        return searchChunkStore.findByCollection(collectionId).stream()
                .filter(chunk -> matchesFilters(chunk, query.filters()))
                .map(chunk -> toCandidate(chunk, keywordScore(chunk, query.keywords()), "sparse"))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed().thenComparing(RetrievalCandidate::chunkId))
                .limit(topK)
                .toList();
    }

    private double keywordScore(Chunk chunk, List<String> keywords) {
        var lower = chunk.content().toLowerCase(java.util.Locale.ROOT);
        return keywords.stream().filter(lower::contains).count();
    }

    private boolean matchesFilters(Chunk chunk, Map<String, Object> filters) {
        for (var entry : filters.entrySet()) {
            if ("collection".equals(entry.getKey())) {
                continue;
            }
            var actual = chunk.metadata().get(entry.getKey());
            if ("tags".equals(entry.getKey()) && actual instanceof List<?> actualTags && entry.getValue() instanceof List<?> expectedTags) {
                if (!Set.copyOf(actualTags).containsAll(expectedTags)) {
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
