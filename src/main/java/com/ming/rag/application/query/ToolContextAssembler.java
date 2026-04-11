package com.ming.rag.application.query;

import com.ming.rag.domain.query.AdvisorDecision;
import com.ming.rag.domain.query.ToolContext;
import com.ming.rag.domain.query.ToolExecutionRequest;
import com.ming.rag.domain.query.ToolExecutionResult;
import com.ming.rag.domain.query.port.ToolRegistryPort;
import com.ming.rag.domain.response.ToolSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ToolContextAssembler {

    private final ToolRegistryPort toolRegistryPort;

    public ToolContextAssembler(ToolRegistryPort toolRegistryPort) {
        this.toolRegistryPort = toolRegistryPort;
    }

    public List<ToolExecutionRequest> buildRequests(String query, AdvisorDecision decision) {
        return toolRegistryPort.resolveTools(decision.candidateTools()).stream()
                .map(tool -> new ToolExecutionRequest(
                        UUID.randomUUID().toString(),
                        tool.serverId(),
                        tool.toolName(),
                        query,
                        Map.of("query", query),
                        0
                ))
                .toList();
    }

    public ToolContext buildContext(boolean invoked, List<ToolExecutionResult> results, boolean fallbackTriggered, String fallbackReason) {
        return new ToolContext(results == null ? List.of() : List.copyOf(results), invoked, fallbackTriggered, fallbackReason);
    }

    public List<ToolSource> toToolSources(ToolContext toolContext) {
        return toolContext.successfulResults().stream()
                .map(result -> new ToolSource(result.serverId(), result.toolName(), result.summary()))
                .toList();
    }

    public String toPromptContext(ToolContext toolContext) {
        return toolContext.successfulResults().stream()
                .map(result -> "[" + result.toolName() + "] " + result.summary())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    public Map<String, Object> toDebug(AdvisorDecision decision, ToolContext toolContext) {
        var debug = new LinkedHashMap<String, Object>();
        debug.put("advisorRoute", decision.route());
        debug.put("mcpInvoked", toolContext.invoked());
        debug.put("mcpFallback", toolContext.fallbackTriggered());
        debug.put("selectedTools", toolContext.results().stream().map(ToolExecutionResult::toolName).toList());
        if (!toolContext.failedResults().isEmpty()) {
            debug.put("mcpFailureType", toolContext.failedResults().getFirst().failureType());
        }
        if (!decision.matchedRules().isEmpty()) {
            debug.put("advisorMatchedRules", decision.matchedRules());
        }
        return Map.copyOf(debug);
    }
}
