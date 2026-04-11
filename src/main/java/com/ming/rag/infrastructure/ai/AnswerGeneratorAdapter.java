package com.ming.rag.infrastructure.ai;

import com.ming.rag.application.query.CitationFactoryService;
import com.ming.rag.domain.query.RankedResult;
import com.ming.rag.domain.query.ToolContext;
import com.ming.rag.domain.response.AnswerResponse;
import com.ming.rag.domain.response.ToolSource;
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
    public AnswerResponse generate(String query, List<RankedResult> rankedResults, ToolContext toolContext, String traceId, boolean debug) {
        var toolSources = toolContext.successfulResults().stream()
                .map(result -> new ToolSource(result.serverId(), result.toolName(), result.summary()))
                .toList();
        if (rankedResults.isEmpty() && toolSources.isEmpty()) {
            return new AnswerResponse(true, "", List.of(), List.of(), traceId, Map.of());
        }
        var top = rankedResults.isEmpty() ? null : rankedResults.getFirst();
        var answer = generateAnswer(query, rankedResults, top, toolSources);
        Map<String, Object> debugPayload = debug
                ? Map.of(
                        "topChunkId", top == null ? "" : top.chunkId(),
                        "resultCount", rankedResults.size(),
                        "chatProvider", chatModelProvider.providerName(),
                        "toolSourceCount", toolSources.size()
                )
                : Map.of();
        return new AnswerResponse(false, answer, citationFactoryService.from(rankedResults), toolSources, traceId, debugPayload);
    }

    private String generateAnswer(String query, List<RankedResult> rankedResults, RankedResult top, List<ToolSource> toolSources) {
        var retrievalContent = rankedResults.stream()
                .limit(3)
                .map(RankedResult::content)
                .reduce((a, b) -> a + "\n---\n" + b)
                .orElse(top == null ? "" : top.content());
        var toolContext = toolSources.stream()
                .map(source -> source.toolName() + ": " + source.summary())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        if (chatModelProvider.isEnabled() && chatModelProvider.isAvailable()) {
            var prompt = """
                    请基于以下检索内容回答问题，保持回答简洁且忠于事实。
                    问题: %s
                    参考内容:
                    %s
                    外部工具补充:
                    %s
                    """.formatted(query, retrievalContent, toolContext);
            return chatModelProvider.require().chat(prompt);
        }
        if (!toolContext.isBlank() && !retrievalContent.isBlank()) {
            return "Based on local knowledge and external tools, " + retrievalContent + " " + toolContext;
        }
        if (!toolContext.isBlank()) {
            return "Based on external tools, " + toolContext;
        }
        return "Based on the retrieved knowledge, " + retrievalContent;
    }
}
