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
    private final ChatModelProvider chatModelProvider;

    public AnswerGeneratorAdapter(CitationFactoryService citationFactoryService, ChatModelProvider chatModelProvider) {
        this.citationFactoryService = citationFactoryService;
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public AnswerResponse generate(String query, List<RankedResult> rankedResults, String traceId, boolean debug) {
        if (rankedResults.isEmpty()) {
            return new AnswerResponse(true, "", List.of(), traceId, Map.of());
        }
        var top = rankedResults.getFirst();
        var answer = generateAnswer(query, rankedResults, top);
        Map<String, Object> debugPayload = debug
                ? Map.of("topChunkId", top.chunkId(), "resultCount", rankedResults.size(), "chatProvider", chatModelProvider.providerName())
                : Map.of();
        return new AnswerResponse(false, answer, citationFactoryService.from(rankedResults), traceId, debugPayload);
    }

    private String generateAnswer(String query, List<RankedResult> rankedResults, RankedResult top) {
        if (chatModelProvider.isEnabled() && chatModelProvider.isAvailable()) {
            var prompt = """
                    请基于以下检索内容回答问题，保持回答简洁且忠于事实。
                    问题: %s
                    参考内容:
                    %s
                    """.formatted(query, rankedResults.stream().limit(3).map(RankedResult::content).reduce((a, b) -> a + "\n---\n" + b).orElse(top.content()));
            return chatModelProvider.require().chat(prompt);
        }
        return "Based on the retrieved knowledge, " + top.content();
    }
}
