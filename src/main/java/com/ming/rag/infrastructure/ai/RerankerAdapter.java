package com.ming.rag.infrastructure.ai;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.query.ProcessedQuery;
import com.ming.rag.domain.query.RankedResult;
import com.ming.rag.domain.query.port.RerankPort;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RerankerAdapter implements RerankPort {

    private final RagProperties ragProperties;

    public RerankerAdapter(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    @Override
    public List<RankedResult> rerank(ProcessedQuery query, List<RankedResult> candidates, int topK) {
        if (!ragProperties.rerank().enabled() || "none".equalsIgnoreCase(ragProperties.rerank().provider())) {
            return limit(candidates, topK);
        }
        try {
            return limit(candidates, topK);
        } catch (RuntimeException ignored) {
            return limit(candidates, topK);
        }
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
