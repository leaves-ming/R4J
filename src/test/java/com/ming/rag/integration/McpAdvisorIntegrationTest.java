package com.ming.rag.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ming.rag.application.ingestion.IngestionApplicationService;
import com.ming.rag.application.ingestion.IngestionCommand;
import com.ming.rag.application.query.QueryApplicationService;
import com.ming.rag.application.query.QueryCommand;
import com.ming.rag.bootstrap.RagApplication;
import com.ming.rag.bootstrap.config.RagProperties;
import com.ming.rag.integration.support.IntegrationTestContainers;
import com.ming.rag.infrastructure.mcp.McpToolRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = RagApplication.class)
@TestPropertySource(properties = {
        "rag.storage.file.base-path=target/test-mcp-advisor-files",
        "rag.storage.search.initialize-index-on-startup=true",
        "rag.storage.search.dev-fallback-enabled=false",
        "rag.ai.chat.provider=none",
        "rag.mcp.enabled=true",
        "rag.mcp.servers[0].server-id=search-mcp",
        "rag.mcp.servers[0].enabled=true",
        "rag.mcp.servers[0].transport=http",
        "rag.mcp.servers[0].endpoint=mock://search",
        "rag.mcp.servers[0].timeout=3s",
        "rag.mcp.servers[0].allowed-tools[0]=search_web",
        "rag.mcp.servers[0].allowed-tools[1]=delete_file",
        "rag.mcp.servers[0].tool-priority=10",
        "rag.advisor.enabled=true",
        "rag.advisor.fallback-enabled=true",
        "rag.advisor.must-rules[0].id=live-search",
        "rag.advisor.must-rules[0].keywords[0]=latest",
        "rag.advisor.realtime-patterns[0]=latest"
})
class McpAdvisorIntegrationTest extends IntegrationTestContainers {

    @Autowired
    private IngestionApplicationService ingestionApplicationService;

    @Autowired
    private QueryApplicationService queryApplicationService;

    @Test
    void shouldUseOnlyAllowedQueryToolsAndIgnoreBlockedTools() {
        ingest("# Policy\nlatest travel policy guidance is documented here");

        var response = queryApplicationService.query(new QueryCommand(
                "latest travel policy guidance",
                "default",
                Map.of(),
                10,
                10,
                10,
                5,
                true
        ));

        assertThat(response.debug().get("advisorRoute")).isEqualTo("must");
        @SuppressWarnings("unchecked")
        var selectedTools = (java.util.List<String>) response.debug().get("selectedTools");
        assertThat(selectedTools).contains("search_web");
        assertThat(selectedTools).doesNotContain("delete_file");
    }

    @Test
    void shouldFailDiscoveryWhenEnabledServerHasNoQueryableToolsAfterFiltering() {
        assertThatThrownBy(() -> new McpToolRegistry(new RagProperties(
                new RagProperties.Ingestion(1000, 200, 100, 5),
                new RagProperties.Query(20, 20, 10, 60),
                new RagProperties.Rerank(false, "none", 5),
                new RagProperties.Ai(
                        new RagProperties.Model("none", null, null, null),
                        new RagProperties.Model("none", null, null, null)
                ),
                new RagProperties.Storage(
                        new RagProperties.Metadata("jdbc:postgresql://localhost:5432/rag", "rag", "rag", true),
                        new RagProperties.Search("http://localhost:9200", "chunk_record_v1", false, true, false),
                        new RagProperties.File("target/test-mcp-fail-files")
                ),
                new RagProperties.Observability(true, true),
                new RagProperties.Evaluation(10, "default"),
                new RagProperties.Mcp(true, java.util.List.of(
                        new RagProperties.Server("bad-mcp", true, "http", "mock://bad", java.util.List.of(), Duration.ofSeconds(3), java.util.List.of("delete_file"), 10)
                )),
                RagProperties.Advisor.disabled()
        ))).hasMessage("Enabled MCP server bad-mcp has no allowed information-query tools after filtering");
    }

    private void ingest(String content) {
        ingestionApplicationService.ingest(new IngestionCommand(
                "default",
                "mcp-advisor.md",
                "text/markdown",
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                false,
                128,
                0
        ));
    }
}
