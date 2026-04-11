package com.ming.rag.infrastructure.mcp;

import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.domain.query.RegisteredTool;
import com.ming.rag.domain.query.port.ToolRegistryPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpToolRegistry implements ToolRegistryPort {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private final List<RegisteredTool> tools;

    public McpToolRegistry(RagProperties ragProperties) {
        var discovered = new ArrayList<RegisteredTool>();
        for (var server : ragProperties.mcp().servers()) {
            if (!server.enabled()) {
                continue;
            }
            var validTools = server.allowedTools().stream()
                    .filter(this::isInformationQueryTool)
                    .map(toolName -> new RegisteredTool(
                            server.serverId(),
                            toolName,
                            "Configured MCP tool",
                            java.util.Map.of("query", "string"),
                            server.toolPriority(),
                            true
                    ))
                    .toList();
            var filteredOutCount = server.allowedTools().size() - validTools.size();
            if (filteredOutCount > 0) {
                log.warn(
                        "mcp registry filtered non-query tools serverId={} filteredCount={} stage=mcp_discovery",
                        server.serverId(),
                        filteredOutCount
                );
            }
            if (validTools.isEmpty()) {
                throw new IllegalStateException("Enabled MCP server " + server.serverId() + " has no allowed information-query tools after filtering");
            }
            log.info(
                    "mcp registry discovered tools serverId={} toolCount={} stage=mcp_discovery",
                    server.serverId(),
                    validTools.size()
            );
            discovered.addAll(validTools);
        }
        this.tools = discovered.stream()
                .sorted(Comparator.comparingInt(RegisteredTool::priority).thenComparing(RegisteredTool::toolName))
                .toList();
    }

    @Override
    public List<RegisteredTool> listRegisteredTools() {
        return tools;
    }

    @Override
    public List<RegisteredTool> resolveTools(List<String> candidateTools) {
        if (candidateTools == null || candidateTools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .filter(tool -> candidateTools.contains(tool.toolName()))
                .toList();
    }

    private boolean isInformationQueryTool(String toolName) {
        var normalized = toolName.toLowerCase(Locale.ROOT);
        return normalized.startsWith("get_")
                || normalized.startsWith("search_")
                || normalized.startsWith("lookup_")
                || normalized.startsWith("query_")
                || normalized.startsWith("fetch_")
                || normalized.startsWith("calculate")
                || normalized.startsWith("time")
                || normalized.startsWith("weather");
    }
}
