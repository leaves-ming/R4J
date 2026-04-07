package com.ming.rag.application.query;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.query.RankedResult;
import com.ming.rag.domain.query.RetrievalCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RrfFusionPolicy {

    private final RagProperties ragProperties;

    public RrfFusionPolicy(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<RankedResult> fuse(List<RetrievalCandidate> dense, List<RetrievalCandidate> sparse, int topK) {
        Map<String, ScoredCandidate> fused = new LinkedHashMap<>();
        accumulate(fused, dense, ragProperties.query().rrfK());
        accumulate(fused, sparse, ragProperties.query().rrfK());

        var ranked = new ArrayList<>(fused.values());
        ranked.sort(Comparator
                .comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(ScoredCandidate::chunkId));

        var limit = Math.min(topK, ranked.size());
        List<RankedResult> results = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            var candidate = ranked.get(i);
            results.add(new RankedResult(candidate.chunkId(), candidate.score(), i + 1, candidate.content(), candidate.metadata()));
        }
        return List.copyOf(results);
    }

    private void accumulate(Map<String, ScoredCandidate> fused, List<RetrievalCandidate> candidates, int rrfK) {
        for (int i = 0; i < candidates.size(); i++) {
            var candidate = candidates.get(i);
            var increment = 1.0d / (rrfK + i + 1);
            fused.compute(candidate.chunkId(), (ignored, current) -> {
                if (current == null) {
                    return new ScoredCandidate(candidate.chunkId(), increment, candidate.content(), candidate.metadata());
                }
                return new ScoredCandidate(current.chunkId(), current.score() + increment, current.content(), current.metadata());
            });
        }
    }

    private record ScoredCandidate(String chunkId, double score, String content, Map<String, Object> metadata) {
    }
}
