package com.ming.rag.infrastructure.ai;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.query.ProcessedQuery;
import com.ming.rag.domain.query.RankedResult;
import com.ming.rag.domain.query.port.RerankPort;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RerankerAdapter implements RerankPort {

    private final RagProperties ragProperties;
    private final RerankerProvider rerankerProvider;

    public RerankerAdapter(RagProperties ragProperties, RerankerProvider rerankerProvider) {
        this.ragProperties = ragProperties;
        this.rerankerProvider = rerankerProvider;
    }

    @Override
    public List<RankedResult> rerank(ProcessedQuery query, List<RankedResult> candidates, int topK) {
        if (!rerankerProvider.isEnabled()) {
            return limit(candidates, topK);
        }
        try {
            rerankerProvider.requireAvailable();
            return rerankDeterministically(query, candidates, topK);
        } catch (RuntimeException ignored) {
            return limit(candidates, topK);
        }
    }

    private List<RankedResult> rerankDeterministically(ProcessedQuery query, List<RankedResult> candidates, int topK) {
        var reranked = candidates.stream()
                .sorted(Comparator
                        .comparingDouble((RankedResult candidate) -> rerankScore(query, candidate)).reversed()
                        .thenComparing(RankedResult::chunkId))
                .limit(topK)
                .toList();
        return java.util.stream.IntStream.range(0, reranked.size())
                .mapToObj(index -> {
                    var candidate = reranked.get(index);
                    return new RankedResult(
                            candidate.chunkId(),
                            rerankScore(query, candidate),
                            index + 1,
                            candidate.content(),
                            candidate.metadata()
                    );
                })
                .toList();
    }

    private double rerankScore(ProcessedQuery query, RankedResult candidate) {
        var lower = candidate.content().toLowerCase(Locale.ROOT);
        var overlap = query.keywords().stream().filter(lower::contains).count();
        var providerBoost = "llm".equalsIgnoreCase(ragProperties.rerank().provider()) ? 0.5d : 0.2d;
        return candidate.score() + overlap + providerBoost;
    }

    private List<RankedResult> limit(List<RankedResult> candidates, int topK) {
        var limited = candidates.stream().limit(topK).toList();
        return java.util.stream.IntStream.range(0, limited.size())
                .mapToObj(index -> {
                    var candidate = limited.get(index);
                    return new RankedResult(candidate.chunkId(), candidate.score(), index + 1, candidate.content(), candidate.metadata());
                })
                .toList();
    }
}
