package com.ming.rag.bootstrap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.rag.domain.query.port.McpExecutionPort;
import com.ming.rag.domain.query.port.ToolRegistryPort;
import com.ming.rag.infrastructure.mcp.McpExecutionGateway;
import com.ming.rag.infrastructure.mcp.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpConfiguration.class);

    @Bean
    ToolRegistryPort toolRegistryPort(RagProperties ragProperties) {
        var registry = new McpToolRegistry(ragProperties);
        if (ragProperties.mcp().enabled()) {
            var enabledServerCount = ragProperties.mcp().servers().stream().filter(RagProperties.Server::enabled).count();
            if (enabledServerCount == 0) {
                throw new IllegalStateException("MCP is enabled but no enabled servers are configured");
            }
            if (registry.listRegisteredTools().isEmpty()) {
                throw new IllegalStateException("MCP is enabled but no tools were discovered");
            }
            log.info(
                    "mcp registry ready enabledServers={} registeredTools={} stage=mcp_discovery",
                    enabledServerCount,
                    registry.listRegisteredTools().size()
            );
        }
        return registry;
    }

    @Bean
    McpExecutionPort mcpExecutionPort(RagProperties ragProperties, ObjectMapper objectMapper) {
        return new McpExecutionGateway(ragProperties, objectMapper);
    }
}
