package com.ming.rag.infrastructure.search;

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
        return searchChunkStore.findRecordsByCollection(collectionId).stream()
                .filter(record -> matchesFilters(record, query.filters()))
                .map(record -> toCandidate(record, keywordScore(searchChunkStore.sparseTermsOf(record), query.keywords()), "sparse"))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingDouble(RetrievalCandidate::score).reversed().thenComparing(RetrievalCandidate::chunkId))
                .limit(topK)
                .toList();
    }

    private double keywordScore(Map<String, Integer> sparseTerms, List<String> keywords) {
        return keywords.stream()
                .map(String::toLowerCase)
                .mapToDouble(keyword -> sparseTerms.getOrDefault(keyword, 0))
                .sum();
    }

    private boolean matchesFilters(com.ming.rag.domain.ingestion.ChunkRecord chunk, Map<String, Object> filters) {
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

    private RetrievalCandidate toCandidate(com.ming.rag.domain.ingestion.ChunkRecord chunk, double score, String matchedBy) {
        var metadata = new java.util.LinkedHashMap<String, Object>(chunk.metadata());
        metadata.put("document_id", chunk.documentId());
        metadata.putIfAbsent("source_path", chunk.metadata().get("source_path"));
        return new RetrievalCandidate(chunk.chunkId(), score, matchedBy, chunk.content(), Map.copyOf(metadata));
    }
}
