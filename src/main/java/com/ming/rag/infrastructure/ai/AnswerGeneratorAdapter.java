package com.ming.rag.infrastructure.ai;

import com.ming.rag.application.query.CitationFactoryService;
import com.ming.rag.domain.query.RankedResult;
import com.ming.rag.domain.response.AnswerResponse;
import com.ming.rag.domain.response.port.AnswerGeneratorPort;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AnswerGeneratorAdapter implements AnswerGeneratorPort {

    private final CitationFactoryService citationFactoryService;

    public AnswerGeneratorAdapter(CitationFactoryService citationFactoryService) {
        this.citationFactoryService = citationFactoryService;
    }

    @Override
    public AnswerResponse generate(String query, List<RankedResult> rankedResults, String traceId, boolean debug) {
        if (rankedResults.isEmpty()) {
            return new AnswerResponse(true, "", List.of(), traceId, Map.of());
        }
        var top = rankedResults.getFirst();
        var answer = "Based on the retrieved knowledge, " + top.content();
        Map<String, Object> debugPayload = debug
                ? Map.of("topChunkId", top.chunkId(), "resultCount", rankedResults.size())
                : Map.of();
        return new AnswerResponse(false, answer, citationFactoryService.from(rankedResults), traceId, debugPayload);
    }
}
